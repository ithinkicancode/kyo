package kyo.bench

import org.openjdk.jmh.annotations._
import cats.effect.IO
import kyo._
import kyo.ios._
import zio.{ZIO, UIO}
import java.util.concurrent.Executors

import kyo.bench.Bench
import java.util.concurrent.CompletableFuture

class BroadFlatMapBench extends Bench.SyncAndFork[BigInt] {

  val depth = 15

  def catsBench(): IO[BigInt] = {
    def catsFib(n: Int): IO[BigInt] =
      if (n <= 1) IO.pure(BigInt(n))
      else
        catsFib(n - 1).flatMap(a => catsFib(n - 2).flatMap(b => IO.pure(a + b)))

    catsFib(depth)
  }

  def kyoBench(): BigInt > IOs = {
    def kyoFib(n: Int): BigInt > IOs =
      if (n <= 1) IOs.value(BigInt(n))
      else kyoFib(n - 1).flatMap(a => kyoFib(n - 2).flatMap(b => IOs.value(a + b)))

    kyoFib(depth)
  }

  def zioBench(): UIO[BigInt] = {
    def zioFib(n: Int): UIO[BigInt] =
      if (n <= 1)
        ZIO.succeed(BigInt(n))
      else
        zioFib(n - 1).flatMap(a => zioFib(n - 2).flatMap(b => ZIO.succeed(a + b)))
    zioFib(depth)
  }

  def oxFib(n: Int): BigInt =
    if (n <= 1) n
    else oxFib(n - 1) + oxFib(n - 2)

  // @Benchmark
  def syncOx(): BigInt = {
    oxFib(depth)
  }

  // @Benchmark
  def forkOx(): BigInt = {
    import ox._
    scoped {
      fork(oxFib(depth)).join()
    }
  }
}
