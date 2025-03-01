package kyo

import scala.Console

import ios._
import envs._

object randoms {

  abstract class Random {
    def nextInt: Int > IOs
    def nextInt(n: Int): Int > IOs
    def nextLong: Long > IOs
    def nextDouble: Double > IOs
    def nextBoolean: Boolean > IOs
    def nextFloat: Float > IOs
    def nextGaussian: Double > IOs
  }

  object Random {
    implicit val default: Random =
      new Random {

        val random          = new java.util.Random
        def nextInt         = IOs(random.nextInt())
        def nextInt(n: Int) = IOs(random.nextInt(n))
        def nextLong        = IOs(random.nextLong())
        def nextDouble      = IOs(random.nextDouble())
        def nextBoolean     = IOs(random.nextBoolean())
        def nextFloat       = IOs(random.nextFloat())
        def nextGaussian    = IOs(random.nextGaussian())
      }
  }

  type Randoms = Envs[Random] with IOs

  object Randoms {

    private val envs = Envs[Random]

    def run[T, S](r: Random)(f: => T > (Randoms with S)): T > (IOs with S) =
      envs.run[T, IOs with S](r)(f)

    def run[T, S](f: => T > (Randoms with S))(implicit r: Random): T > (IOs with S) =
      run[T, S](r)(f)

    def nextInt: Int > Randoms =
      envs.get.map(_.nextInt)

    def nextInt[S](n: Int > S): Int > (S with Randoms) =
      n.map(n => envs.get.map(_.nextInt(n)))

    def nextLong: Long > Randoms =
      envs.get.map(_.nextLong)

    def nextDouble: Double > Randoms =
      envs.get.map(_.nextDouble)

    def nextBoolean: Boolean > Randoms =
      envs.get.map(_.nextBoolean)

    def nextFloat: Float > Randoms =
      envs.get.map(_.nextFloat)

    def nextGaussian: Double > Randoms =
      envs.get.map(_.nextGaussian)

    def nextValue[T, S](seq: Seq[T] > S): T > (S with Randoms) =
      seq.map(s => nextInt(s.size).map(idx => s(idx)))
  }
}
