import besom.*
import scala.concurrent.duration.*

import besom.internal.CustomTimeouts
import besom.api.{kubernetes => k8s}
import k8s.core.v1.enums.*
import k8s.core.v1.inputs.*
import k8s.apps.v1.inputs.*
import k8s.meta.v1.inputs.*
import k8s.apps.v1.{Deployment, DeploymentArgs, StatefulSet, StatefulSetArgs}
import k8s.core.v1.{Namespace, Service, ServiceArgs}
import k8s.networking.v1.{Ingress, IngressArgs}
import k8s.networking.v1.inputs.{
  IngressSpecArgs,
  IngressRuleArgs,
  HttpIngressRuleValueArgs,
  HttpIngressPathArgs,
  IngressBackendArgs,
  IngressServiceBackendArgs,
  ServiceBackendPortArgs
}

import besom.cfg.k8s.ConfiguredContainerArgs
import besom.cfg.Struct

case class PostgresArgs private (
  port: Output[Int],
  dashboardPort: Output[Int]
)
object PostgresArgs:
  def apply(port: Input[Int], dashboardPort: Input[Int])(using Context): PostgresArgs =
    new PostgresArgs(port.asOutput(), dashboardPort.asOutput())

case class AppArgs private (
  name: Output[NonEmptyString],
  replicas: Output[Int],
  containerPort: Output[Int],
  servicePort: Output[Int],
  host: Output[NonEmptyString],
  openAIToken: Output[String],
  docsBaseUrl: Output[String]
)
object AppArgs:
  def apply(
    name: Input[NonEmptyString],
    replicas: Input[Int],
    containerPort: Input[Int],
    servicePort: Input[Int],
    host: Input[NonEmptyString],
    openAIToken: Input[String],
    docsBaseUrl: Input[String]
  )(using Context): AppArgs =
    new AppArgs(
      name.asOutput(),
      replicas.asOutput(),
      containerPort.asOutput(),
      servicePort.asOutput(),
      host.asOutput(),
      openAIToken.asOutput(),
      docsBaseUrl.asOutput()
    )

case class AppDeploymentArgs(
  postgresArgs: PostgresArgs,
  appArgs: AppArgs
)

case class AppDeployment(
  jdbcUrl: Output[String],
  appUrl: Output[String]
)(using ComponentBase)
    extends ComponentResource
    derives RegistersOutputs

object AppDeployment:
  def apply(name: NonEmptyString, args: AppDeploymentArgs, resourceOpts: ComponentResourceOptions)(using Context): Output[AppDeployment] =
    component(name, "user:component:app-deployment", resourceOpts) {
      val labels   = Map("app" -> name)
      val dbLabels = Map("db" -> name)

      val appNamespace = Namespace(name)

      val openAIToken   = args.appArgs.openAIToken
      val postgresPort  = args.postgresArgs.port
      val dashboardPort = args.postgresArgs.dashboardPort
      val containerPort = args.appArgs.containerPort
      val servicePort   = args.appArgs.servicePort
      val ingressHost   = args.appArgs.host
      val docsBaseUrl   = args.appArgs.docsBaseUrl

      val postgresmlStatefulSet = k8s.apps.v1.StatefulSet(
        "postgresml",
        k8s.apps.v1.StatefulSetArgs(
          metadata = ObjectMetaArgs(
            name = "postgresml",
            namespace = appNamespace.metadata.name,
            labels = dbLabels
          ),
          spec = StatefulSetSpecArgs(
            serviceName = "postgresml",
            replicas = 1,
            selector = LabelSelectorArgs(matchLabels = dbLabels),
            template = PodTemplateSpecArgs(
              metadata = ObjectMetaArgs(
                labels = dbLabels
              ),
              spec = PodSpecArgs(
                containers = ContainerArgs(
                  name = "postgresml",
                  image = "ghcr.io/postgresml/postgresml:2.8.2",
                  args = List("tail", "-f", "/dev/null"),
                  readinessProbe = ProbeArgs(
                    exec = ExecActionArgs(
                      command = List("psql", "-d", "postgresml", "-c", "SELECT 1")
                    ),
                    initialDelaySeconds = 15,
                    timeoutSeconds = 2
                  ),
                  livenessProbe = ProbeArgs(
                    exec = ExecActionArgs(
                      command = List("psql", "-d", "postgresml", "-c", "SELECT 1")
                    ),
                    initialDelaySeconds = 45,
                    timeoutSeconds = 2
                  ),
                  ports = List(
                    ContainerPortArgs(name = "postgres", containerPort = postgresPort),
                    ContainerPortArgs(name = "dashboard", containerPort = dashboardPort)
                  )
                ) :: Nil
              )
            )
          )
        ),
        opts(customTimeouts = CustomTimeouts(create = 10.minutes))
      )

      val postgresMlService = Service(
        "postgresml-svc",
        ServiceArgs(
          spec = ServiceSpecArgs(
            selector = dbLabels,
            ports = List(
              ServicePortArgs(name = "postgres", port = postgresPort, targetPort = postgresPort),
              ServicePortArgs(name = "dashboard", port = dashboardPort, targetPort = dashboardPort)
            )
          ),
          metadata = ObjectMetaArgs(
            namespace = appNamespace.metadata.name,
            labels = labels
          )
        ),
        opts(
          dependsOn = postgresmlStatefulSet
        )
      )

      val postgresmlHost = postgresMlService.metadata.name.getOrFail(Exception("postgresml service name not found!"))
      val jdbcUrl        = p"jdbc:postgresql://${postgresmlHost}:${postgresPort}/postgresml"

      val appDeployment =
        Deployment(
          name,
          DeploymentArgs(
            spec = DeploymentSpecArgs(
              selector = LabelSelectorArgs(matchLabels = labels),
              replicas = 1,
              template = PodTemplateSpecArgs(
                metadata = ObjectMetaArgs(
                  name = p"$name-deployment",
                  labels = labels,
                  namespace = appNamespace.metadata.name
                ),
                spec = PodSpecArgs(
                  containers = ConfiguredContainerArgs(
                    name = "app",
                    image = "ghcr.io/lbialy/askme:0.1.0",
                    configuration = Struct(
                      port = containerPort,
                      openAIApiKey = openAIToken,
                      jdbcUrl = jdbcUrl,
                      docsBaseUrl = docsBaseUrl
                    ),
                    ports = List(
                      ContainerPortArgs(name = "http", containerPort = containerPort)
                    ),
                    readinessProbe = ProbeArgs(
                      httpGet = HttpGetActionArgs(
                        path = "/",
                        port = containerPort
                      ),
                      initialDelaySeconds = 10,
                      periodSeconds = 5
                    ),
                    livenessProbe = ProbeArgs(
                      httpGet = HttpGetActionArgs(
                        path = "/",
                        port = containerPort
                      ),
                      initialDelaySeconds = 10,
                      periodSeconds = 5
                    )
                  ) :: Nil
                )
              )
            ),
            metadata = ObjectMetaArgs(
              namespace = appNamespace.metadata.name
            )
          )
        )

      val appService =
        Service(
          s"$name-svc",
          ServiceArgs(
            spec = ServiceSpecArgs(
              selector = labels,
              ports = List(
                ServicePortArgs(name = "http", port = servicePort, targetPort = containerPort)
              ),
              `type` = ServiceSpecType.ClusterIP
            ),
            metadata = ObjectMetaArgs(
              namespace = appNamespace.metadata.name,
              labels = labels
            )
          ),
          opts(deleteBeforeReplace = true)
        )

      val appIngress =
        Ingress(
          s"$name-ingress",
          IngressArgs(
            spec = IngressSpecArgs(
              rules = List(
                IngressRuleArgs(
                  host = ingressHost,
                  http = HttpIngressRuleValueArgs(
                    paths = List(
                      HttpIngressPathArgs(
                        path = "/",
                        pathType = "Prefix",
                        backend = IngressBackendArgs(
                          service = IngressServiceBackendArgs(
                            name = appService.metadata.name.getOrElse(name),
                            port = ServiceBackendPortArgs(
                              number = servicePort
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            ),
            metadata = ObjectMetaArgs(
              namespace = appNamespace.metadata.name,
              labels = labels,
              annotations = Map(
                "kubernetes.io/ingress.class" -> "traefik"
              )
            )
          )
        )

      // use all of the above and return final url
      val appUrl =
        for
          _   <- appNamespace
          _   <- postgresmlStatefulSet
          _   <- postgresMlService
          _   <- appDeployment
          _   <- appService
          _   <- appIngress
          url <- p"https://$ingressHost/"
        yield url

      AppDeployment(jdbcUrl, appUrl)
    }
