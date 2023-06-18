package kyo.concurrent.scheduler

import kyo.concurrent.fibers.Fibers
import kyo._
import kyo.ios._
import kyo.loggers.Loggers

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import scala.annotation.tailrec
import scala.util.control.NonFatal

import IOPromise._
import java.util.concurrent.locks.LockSupport

private[kyo] class IOPromise[T](state: State[T])
    extends AtomicReference(state) {

  def this() = this(Pending())

  /*inline*/
  final def isDone(): Boolean = {
    @tailrec def loop(promise: IOPromise[T]): Boolean =
      promise.get() match {
        case p: Pending[T] @unchecked =>
          false
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case _ =>
          true
      }
    loop(this)
  }

  /*inline*/
  final def interrupts(p: IOPromise[_]): Unit =
    onComplete { _ =>
      p.interrupt()
    }

  /*inline*/
  final def interrupt(): Boolean = {
    @tailrec def loop(promise: IOPromise[T]): Boolean =
      promise.get() match {
        case p: Pending[T] @unchecked =>
          promise.complete(p, IOs(throw Fibers.Interrupted())) || loop(promise)
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case _ =>
          false
      }
    loop(this)
  }

  private def compress(): IOPromise[T] = {
    @tailrec def loop(p: IOPromise[T]): IOPromise[T] =
      p.get() match {
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case _ =>
          p
      }
    loop(this)
  }

  private def merge(p: Pending[T]): Unit = {
    @tailrec def loop(promise: IOPromise[T]): Unit =
      promise.get() match {
        case p2: Pending[T] @unchecked =>
          if (!promise.compareAndSet(p2, p2.merge(p)))
            loop(promise)
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case v =>
          p.flush(v.asInstanceOf[T > IOs])
      }
    loop(this)
  }

  final def become(other: IOPromise[T]): Boolean = {
    @tailrec def loop(other: IOPromise[T]): Boolean =
      get() match {
        case p: Pending[T] @unchecked =>
          if (compareAndSet(p, Linked(other))) {
            other.merge(p)
            true
          } else {
            loop(other)
          }
        case _ =>
          false
      }
    loop(other.compress())
  }

  /*inline*/
  final def onComplete( /*inline*/ f: T > IOs => Unit): Unit = {
    @tailrec def loop(promise: IOPromise[T]): Unit =
      promise.get() match {
        case p: Pending[T] @unchecked =>
          if (!promise.compareAndSet(p, p.add(f)))
            loop(promise)
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case v =>
          try f(v.asInstanceOf[T > IOs])
          catch {
            case ex if NonFatal(ex) =>
              log.error("uncaught exception", ex)
          }
      }
    loop(this)
  }

  protected def onComplete(): Unit = {}

  private def complete(p: Pending[T], v: T > IOs): Boolean =
    compareAndSet(p, v) && {
      onComplete()
      p.flush(v)
      true
    }

  /*inline*/
  final def complete(v: T > IOs): Boolean = {
    @tailrec def loop(): Boolean =
      get() match {
        case p: Pending[T] @unchecked =>
          complete(p, v) || loop()
        case _ =>
          false
      }
    loop()
  }

  final def block(): T = {
    def loop(promise: IOPromise[T]): T =
      promise.get() match {
        case _: Pending[T] @unchecked =>
          val b = new (T > IOs => Unit) with (() => T > IOs) {
            @volatile private[this] var result: T > IOs = null.asInstanceOf[T > IOs]
            private[this] val waiterThread    = Thread.currentThread()
            def apply(v: T > IOs) = {
              result = v
              LockSupport.unpark(waiterThread)
            }
            def apply() = result
          }
          onComplete(b)
          @tailrec def loop(): T =
            b() match {
              case null =>
                LockSupport.park()
                loop()
              case v =>
                IOs.run(v)
            }
          loop()
        case l: Linked[T] @unchecked =>
          loop(l.p)
        case v =>
          v.asInstanceOf[T]
      }
    Scheduler.flush()
    loop(this)
  }
}

private[kyo] object IOPromise {

  private val log = Loggers.init(getClass())

  type State[T] = Any // (T > IOs) | Pending[T] | Linked[T]

  case class Linked[T](p: IOPromise[T])

  abstract class Pending[T] { self =>

    def run(v: T > IOs): Pending[T]

    def add(f: T > IOs => Unit): Pending[T] =
      new Pending[T] {
        def run(v: T > IOs) = {
          try f(v)
          catch {
            case ex if NonFatal(ex) =>
              IOs.run(log.error("uncaught exception", ex))
          }
          self
        }
      }

    final def merge(tail: Pending[T]): Pending[T] = {
      @tailrec def loop(p: Pending[T], v: T > IOs): Pending[T] =
        p match {
          case _ if (p eq Pending.Empty) =>
            tail
          case p: Pending[T] =>
            loop(p.run(v), v)
        }
      new Pending[T] {
        def run(v: T > IOs) =
          loop(self, v)
      }
    }

    final def flush(v: T > IOs): Unit = {
      @tailrec def loop(p: Pending[T]): Unit =
        p match {
          case _ if (p eq Pending.Empty) => ()
          case p: Pending[T] =>
            loop(p.run(v))
        }
      loop(this)
    }
  }

  object Pending {
    def apply[T](): Pending[T] = Empty.asInstanceOf[Pending[T]]
    case object Empty extends Pending[Nothing] {
      def run(v: Nothing > IOs) = this
    }
  }
}
