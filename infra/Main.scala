import besom.*
import besom.api.hcloud
import hcloud.inputs.*
import besom.api.{kubernetes => k8s}
import besom.api.{cloudflare => cf}

@main def main = Pulumi.run {

  val locations = Vector("fsn1", "nbg1", "hel1")

  val sshPublicKey  = config.requireString("ssh_public_key_path").map(os.Path(_)).map(os.read(_))
  val sshPrivateKey = config.requireString("ssh_private_key_path").map(os.Path(_)).map(os.read(_))

  val hcloudProvider = hcloud.Provider(
    "hcloud",
    hcloud.ProviderArgs(
      token = config.requireString("hcloud_token")
    )
  )

  val sshKey = hcloud.SshKey(
    "ssh-key",
    hcloud.SshKeyArgs(
      name = "ssh-key",
      publicKey = sshPublicKey
    ),
    opts(provider = hcloudProvider)
  )

  val serverPool = (1 to 3).map { i =>
    hcloud
      .Server(
        s"k3s-server-$i",
        hcloud.ServerArgs(
          serverType = "cx21",
          name = s"k3s-server-$i",
          image = "ubuntu-22.04",
          location = locations(i % locations.size),
          sshKeys = List(sshKey.name),
          publicNets = List(
            ServerPublicNetArgs(
              ipv4Enabled = true,
              ipv6Enabled = false
            )
          )
        ),
        opts(provider = hcloudProvider)
      )
  }.toVector

  val spawnNodes = serverPool.parSequence

  val nodeIps = serverPool.map(_.ipv4Address).parSequence

  val clusterName = "askme-dev"

  val ghcrToken = config.requireString("github_docker_token").flatMap(_.toNonEmptyOutput)

  val k3s = K3S(
    clusterName,
    K3SArgs(
      clusterName = clusterName,
      servers = serverPool.map(_.ipv4Address),
      privateKey = sshPrivateKey,
      k3sVersion = "v1.29.2+k3s1",
      registryAuth = AuthArgs("ghcr.io", "lbialy", ghcrToken)
    ),
    ComponentResourceOptions(
      deletedWith = serverPool.headOption.getOrElse(None)
    )
  )

  val writeKubeconfig = k3s.flatMap { k3s =>
    k3s.kubeconfig.map { kubeconfig =>
      os.write.over(os.pwd / "kubeconfig.conf", kubeconfig)
    }
  }

  val k3sProvider = k8s.Provider(
    "k8s",
    k8s.ProviderArgs(
      kubeconfig = k3s.flatMap(_.kubeconfig)
    )
  )

  val app = AppDeployment(
    "askme",
    AppDeploymentArgs(
      PostgresArgs(
        port = 5432,
        dashboardPort = 8000
      ),
      AppArgs(
        name = "askme",
        replicas = 1,
        containerPort = 8080,
        servicePort = 8080,
        host = "machinespir.it",
        openAIToken = config.requireString("openai_token"),
        docsBaseUrl = "https://virtuslab.github.io/besom/docs/"
      )
    ),
    ComponentResourceOptions(
      providers = k3sProvider,
      deletedWith = k3s
    )
  )

  val cfProvider = cf.Provider(
    "cloudflare-provider",
    cf.ProviderArgs(
      apiToken = config.requireString("cloudflare_token")
    )
  )

  val aRecords = serverPool.zipWithIndex.map { case (server, idx) =>
    val recordIdx = idx + 1
    cf.Record(
      s"askme-a-record-$recordIdx",
      cf.RecordArgs(
        name = "machinespir.it",
        `type` = "A",
        value = server.ipv4Address,
        zoneId = config.requireString("cloudflare_zone_id"),
        ttl = 1,
        proxied = true
      ),
      opts(provider = cfProvider)
    )
  }.parSequence

  Stack(spawnNodes, writeKubeconfig, k3s, app, aRecords).exports(
    nodes = nodeIps,
    kubeconfigPath = (os.pwd / "kubeconfig.conf").toString,
    url = app.flatMap(_.appUrl)
  )
}
