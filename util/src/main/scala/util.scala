package salvo.util

import java.io.{ BufferedInputStream, ByteArrayInputStream, BufferedOutputStream, FileOutputStream, FileInputStream }
import java.nio.file.{ Paths, Files }
import java.net.{ InetAddress, InetSocketAddress, NetworkInterface, URI, URL }
import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils.{ copy => copyStream, toByteArray => readStream }
import org.apache.commons.io.FileUtils.iterateFilesAndDirs
import org.apache.commons.io.filefilter.TrueFileFilter

object `package` extends Logging {
  def ???[T]: T = throw new UnsupportedOperationException("not implemented")

  lazy val Build: String = Option(System.getProperty("salvo.build")).map(_.trim).filterNot(_ == "").getOrElse("dev")

  type File = java.io.File
  type Path = java.nio.file.Path

  val allCatch = scala.util.control.Exception.allCatch

  val PWD = Paths.get("").toAbsolutePath()

  implicit def fileToPath(file: File): Path = Paths.get(file.toURI).toAbsolutePath()
  implicit def stringToPath(path: String): Path = Paths.get(path).toAbsolutePath()
  implicit def pathToFile(path: Path): File = path.toAbsolutePath().toFile
  implicit def pathToURI(path: Path): URI = path.toAbsolutePath().toURI

  def exists(path: Path) = path.exists

  def directory(path: Path) = exists(path) && path.isDirectory

  def symlink(path: Path) = Files.isSymbolicLink(path)

  def mkdir(path: Path): Option[Path] =
    if (directory(path)) None
    else Some(path).filter(_ => path.mkdirs())

  def handleExisting(ignoreExisting: Boolean)(path: Path) =
    if (ignoreExisting) Option(path)
    else sys.error(path.toAbsolutePath()+" already exists")

  def mkdirOrElse(ignoreExisting: Boolean)(path: Path) = mkdir(path) orElse handleExisting(ignoreExisting)(path)

  def mv(src: Path, dst: Path) = src.renameTo(dst)

  implicit def pimpPath(path: Path) = new PimpedPath(path)

  def useAndReturn[A, B](resource0: => A)(op: A => B): A = {
    val resource = resource0
    op(resource)
    resource
  }

  def ifaces() = {
      def filter(iface: NetworkInterface) = !iface.isLoopback && iface.isUp && !iface.isVirtual
    NetworkInterface.getNetworkInterfaces.filter(filter).toList
  }
  def addrs() = ifaces().flatMap(_.getInetAddresses())
  def ipv4_?(addr: InetAddress) = addr.getAddress().size == 4
  def oneAddr(f: InetAddress => Boolean) = addrs.filter(f) match {
    case one :: Nil => one
    case _          => InetAddress.getByName("0.0.0.0")
  }

  def readFrom(from: Path): Array[Byte] =
    readStream(new FileInputStream(from))

  implicit def bytes2string(a: Array[Byte]): String = new String(a, "UTF-8")

  def writeTo(bytes: Array[Byte], to: Path, append: Boolean = false): Unit = {
    val out = new BufferedOutputStream(new FileOutputStream(to, append))
    copyStream(
      new BufferedInputStream(new ByteArrayInputStream(bytes)),
      out)
    out.flush()
    out.close()
  }

  implicit def string2bytes(s: String): Array[Byte] = s.getBytes("UTF-8")

  def traverse(path: Path): Iterator[Path] =
    iterateFilesAndDirs(path, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).map(fileToPath)

  def GET(url: URL) = new {
    def stream = allCatch.either(url.openConnection().getInputStream)
    def string = for (s <- stream.right) yield new String(readStream(s), "UTF-8")
    def save(path: Path) = for (s <- stream.right) yield copyStream(s, new FileOutputStream(path))
  }

  def socketAddress(hostAndPort: String): InetSocketAddress =
    hostAndPort.split(":") match {
      case Array(host, port) => new InetSocketAddress(host, port.toInt)
      case _                 => sys.error("unable to read "+hostAndPort+" as a InetSocketAddress")
    }

  def socketAddress(port: Int): InetSocketAddress = socketAddress("0.0.0.0:"+port)
}

class PimpedPath(path: Path) {
  pimped =>
  def /(other: Path): Path = path.resolve(other).toAbsolutePath()
  def /(other: File): Path = pimped / (other: Path)
  def /(other: String): Path = path.resolve(other).toAbsolutePath()
}
