package kyo.concurrent

import kyo._
import kyo.concurrent.scheduler.Threads
import kyo.envs._
import kyo.ios._

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object timers {

  abstract class Timer {

    def shutdown: Unit > IOs

    def schedule(delay: Duration)(f: => Unit > IOs): TimerTask > IOs

    def scheduleAtFixedRate(
        initalDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > IOs

    def scheduleWithFixedDelay(
        initalDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > IOs
  }

  object Timer {

    implicit val default: Timer =
      new Timer {

        private val exec =
          Executors.newScheduledThreadPool(
              Runtime.getRuntime.availableProcessors / 2,
              Threads("kyo-timer-default")
          )

        private final class Task(task: ScheduledFuture[_]) extends TimerTask {
          def cancel: Boolean > IOs      = IOs(task.cancel(false))
          def isCancelled: Boolean > IOs = IOs(task.isCancelled())
          def isDone: Boolean > IOs      = IOs(task.isDone())
        }

        def shutdown = IOs.unit

        def schedule(delay: Duration)(f: => Unit > IOs) =
          if (delay.isFinite) {
            val call = new Callable[Unit] {
              def call: Unit = IOs.run(f)
            }
            IOs(new Task(exec.schedule(call, delay.toNanos, TimeUnit.NANOSECONDS)))
          } else {
            TimerTask.noop
          }

        def scheduleAtFixedRate(
            initalDelay: Duration,
            period: Duration
        )(f: => Unit > IOs) =
          if (period.isFinite && initalDelay.isFinite) {
            IOs(new Task(
                exec.scheduleAtFixedRate(
                    () => IOs.run(f),
                    initalDelay.toNanos,
                    period.toNanos,
                    TimeUnit.NANOSECONDS
                )
            ))
          } else {
            TimerTask.noop
          }

        def scheduleWithFixedDelay(
            initalDelay: Duration,
            period: Duration
        )(f: => Unit > IOs) =
          if (period.isFinite && initalDelay.isFinite) {
            IOs(new Task(
                exec.scheduleWithFixedDelay(
                    () => IOs.run(f),
                    initalDelay.toNanos,
                    period.toNanos,
                    TimeUnit.NANOSECONDS
                )
            ))
          } else {
            TimerTask.noop
          }
      }
  }

  abstract class TimerTask {
    def cancel: Boolean > IOs
    def isCancelled: Boolean > IOs
    def isDone: Boolean > IOs
  }

  object TimerTask {
    val noop = new TimerTask {
      def cancel      = false
      def isCancelled = false
      def isDone      = true
    }
  }

  type Timers = Envs[Timer] with IOs

  object Timers {

    private val envs = Envs[Timer]

    def run[T, S](t: Timer > S)(f: => T > (Timers with S)): T > (IOs with S) =
      t.map(t => envs.run[T, IOs with S](t)(f))

    def run[T, S](f: => T > (Timers with S))(implicit t: Timer): T > (IOs with S) =
      run[T, IOs with S](t)(f)

    def shutdown: Unit > Timers =
      envs.get.map(_.shutdown)

    def schedule(delay: Duration)(f: => Unit > IOs): TimerTask > Timers =
      envs.get.map(_.schedule(delay)(f))

    def scheduleAtFixedRate(
        period: Duration
    )(f: => Unit > IOs): TimerTask > Timers =
      scheduleAtFixedRate(Duration.Zero, period)(f)

    def scheduleAtFixedRate(
        initialDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > Timers =
      envs.get.map(_.scheduleAtFixedRate(initialDelay, period)(f))

    def scheduleWithFixedDelay(
        period: Duration
    )(f: => Unit > IOs): TimerTask > Timers =
      scheduleWithFixedDelay(Duration.Zero, period)(f)

    def scheduleWithFixedDelay(
        initialDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > Timers =
      envs.get.map(_.scheduleWithFixedDelay(initialDelay, period)(f))
  }
}
