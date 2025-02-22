package mill.util

import mill.define._
import mill.api.Result
import mill.api.Result.OuterStack
import utest.assert

import scala.collection.mutable

object TestUtil {
  object test {

    def anon(inputs: Task[Int]*): Test = new Test(inputs)
    def apply(inputs: Task[Int]*)(implicit ctx: mill.define.Ctx): TestTarget = {
      new TestTarget(inputs, pure = inputs.nonEmpty)
    }
  }

  class Test(val inputs: Seq[Task[Int]]) extends Task[Int] {
    var counter = 0
    var failure = Option.empty[String]
    var exception = Option.empty[Throwable]
    override def evaluate(args: mill.api.Ctx) = {
      failure.map(Result.Failure(_)) orElse
        exception.map(Result.Exception(_, new OuterStack(Nil))) getOrElse
        Result.Success(counter + args.args.map(_.asInstanceOf[Int]).sum)
    }
    override def sideHash = counter + failure.hashCode() + exception.hashCode()
  }

  /**
   * A dummy target that takes any number of inputs, and whose output can be
   * controlled externally, so you can construct arbitrary dataflow graphs and
   * test how changes propagate.
   */
  class TestTarget(taskInputs: Seq[Task[Int]], val pure: Boolean)(implicit ctx0: mill.define.Ctx)
      extends TargetImpl[Int](
        null,
        ctx0,
        upickle.default.readwriter[Int],
        None
      ) {
    override def evaluate(args: mill.api.Ctx) = testTask.evaluate(args)
    override val inputs = taskInputs
    val testTask = new Test(taskInputs)
    def counter_=(i: Int) = testTask.counter = i
    def counter = testTask.counter
    def failure_=(s: Option[String]) = testTask.failure = s
    def failure = testTask.failure
    def exception_=(s: Option[Throwable]) = testTask.exception = s
    def exception = testTask.exception

    override def sideHash = testTask.sideHash
  }

  def checkTopological(targets: Seq[Task[?]]) = {
    val seen = mutable.Set.empty[Task[?]]
    for (t <- targets.reverseIterator) {
      seen.add(t)
      for (upstream <- t.inputs) {
        assert(!seen(upstream))
      }
    }
  }

  def disableInJava9OrAbove(reason: String)(f: => Any): Any = {
    if (System.getProperty("java.specification.version").startsWith("1.")) {
      f
    } else {
      s"*** Disabled in Java 9+ - Reason: ${reason} ***"
    }
  }
}
