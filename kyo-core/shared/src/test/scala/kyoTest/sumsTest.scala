package kyoTest

import kyo._
import kyo.sums._

import scala.util.Success
import scala.util.Try

class sumsTest extends kyoTest.KyoTest {
  "int" in {
    val v: List[Int] > Sums[Int] =
      for {
        _  <- Sums[Int].add(1)
        v1 <- Sums[Int].get
        _  <- Sums[Int].add(1)
        v2 <- Sums[Int].get
        _  <- Sums[Int].add(1)
        v3 <- Sums[Int].get
      } yield List(v1, v2, v3)

    assert(Sums[Int].run(v) == List(1, 2, 3))
  }
  "string" in {
    val v: List[String] > Sums[String] =
      for {
        _  <- Sums[String].add("1")
        v1 <- Sums[String].get
        _  <- Sums[String].add("2")
        v2 <- Sums[String].get
        _  <- Sums[String].add("3")
        v3 <- Sums[String].get
      } yield List(v1, v2, v3)
    val res = Sums[String].run(v)
    assert(res == List("1", "12", "123"))
  }
  "int and string" in {
    val v: (Int, String) > (Sums[Int] with Sums[String]) =
      for {
        _  <- Sums[Int].add(1)
        _  <- Sums[String].add("1")
        _  <- Sums[Int].add(1)
        _  <- Sums[String].add("2")
        _  <- Sums[Int].add(1)
        _  <- Sums[String].add("3")
        v1 <- Sums[Int].get
        v2 <- Sums[String].get
      } yield (v1, v2)
    val res: (Int, String) =
      Sums[String].run(Sums[Int].run[(Int, String), Sums[String]](v)).pure
    assert(res == (3, "123"))
  }
  "initial value" in {
    val r: Int =
      Sums[Int].run(Sums[Int].get).pure
    assert(r == 0)

    val s: String =
      Sums[String].run(Sums[String].get).pure
    assert(s == "")
  }
  "list" in {
    val v =
      for {
        _  <- Sums[List[Int]].add(List(1))
        v1 <- Sums[List[Int]].get
        _  <- Sums[List[Int]].add(List(2))
        v2 <- Sums[List[Int]].get
        _  <- Sums[List[Int]].add(List(3))
        v3 <- Sums[List[Int]].get
      } yield (v1, v2, v3)
    val res = Sums[List[Int]].run(v)
    assert(res == (List(1), List(1, 2), List(1, 2, 3)))
  }
}
