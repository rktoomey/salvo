package salvo.tree.test

import org.specs2.mutable._
import salvo.util._
import salvo.tree._
import Dir._
import TailSpec._
import java.nio.file._
import org.apache.commons.io.FileUtils.deleteDirectory

class TailSpec extends Specification with TestUtils {
  "Tail" should {
    "return no events" in new NnewDirs {
      def n = 0
      attempt("no events")(expected = 0)(identity) must beSome.which(_.isEmpty)
    }
    "generate a specific number of events" in {
        def nEvents(size: Int) =
          "generate %d events".format(size) in new NnewDirs {
            def n = size
            polled must beSome.which(_.size == size)
          }
      nEvents(1)
      nEvents(2)
      nEvents(3)
      nEvents(4)
    }
  }
}

object TailSpec extends TestUtils {
  trait NnewDirs extends UsingTempDir {
    def n: Int
    val tail = {
      val tail = new DirTail(tempDir)
      println("spawning Tail in %s".format(tail.dir))
      tail.start()
      tail
    }
    def attempt[T](tag: String)(attempts: Int = 100, delay: Long = 300L, expected: Int)(f: List[Dir] => T): Option[T] = {
      val (satisfied, dirs) =
        (1 to attempts).foldLeft(false -> List.empty[Dir]) {
          case (acc @ (true, _), _) => acc
          case ((satisfied, dirs), attemptNumber) =>
            println("%s: attempt #%d...".format(tag, attemptNumber))
            Thread.sleep(delay)
            val polled = tail.poll().toList ::: dirs
            val satisfied = polled.size >= expected
            println("%s: satisfied=%s, polled=%d".format(tag, satisfied, polled.size))
            satisfied -> polled
        }
      if (satisfied) Some(f(dirs)) else None
    }
    override def cleanup() {
      tail.stop()
    }
    def create() {
      val path = tail.dir / Dir.init().path
      val created = path.mkdirs()
      println("NnewDirs(%d): created %s: %s".format(n, path, created))
    }
    (1 to n).foreach(_ => create())
    lazy val polled = attempt("%d new dirs".format(n))(expected = n)(identity)
  }
}
