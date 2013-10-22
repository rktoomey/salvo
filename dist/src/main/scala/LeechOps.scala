package salvo.dist

import salvo.util._
import salvo.tree._
import scala.collection.JavaConversions._
import java.net.{ InetAddress, InetSocketAddress, HttpURLConnection, URL }
import java.io.{ File, FileOutputStream }
import com.turn.ttorrent.common.Torrent
import com.turn.ttorrent.client.{ SharedTorrent, Client }
import org.apache.commons.io.FileUtils.moveToDirectory
import org.apache.commons.io.IOUtils.{ copy => copyStream }

trait LeechOps {
  dist: Dist =>

  class Leech(version: Version, server: InetSocketAddress, addr: InetAddress = oneAddr(ipv4_?)) {
    val fileName = version+".torrent"
    val url = new URL("http://"+server.getHostName()+":"+server.getPort()+"/"+fileName)
    val file = dir / fileName
    lazy val torrent = {
      copyStream(url.openConnection().getInputStream(), new FileOutputStream(new File(file)))
      Torrent.load(file)
    }
    lazy val dest = tree.incoming / tree.incoming.create(version).map(_.path).getOrElse(sys.error("???"))
    lazy val shared = new SharedTorrent(torrent, dest, false)
    lazy val client = new Client(addr, shared)
    def move() {
      val erroneous = dest / version.toString
      val contents: List[File] = erroneous.listFiles.toList
      for (entry <- contents) moveToDirectory(entry, dest, false)
      erroneous.delete()
    }
    def seed(duration: Int = 3600) = new SecondarySeed(version, duration, addr)
    def start() {
      client.download()
    }
    def stop() {
      client.stop()
    }
  }
}
