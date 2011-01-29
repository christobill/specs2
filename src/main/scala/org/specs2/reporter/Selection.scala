package org.specs2
package reporter

import main.Arguments
import control.LazyParameters._
import specification._
import Fragments._
import SpecsArguments._

/**
 * The Selection trait implements the logic for filtering the fragments to execute
 * and sorting them according to their dependencies 
 *
 */
trait Selection {
  /** select function returning a filtered and ordered seq of seq of Fragments */
  def select(implicit arguments: Arguments): Fragments => Seq[FragmentSeq]
}

/**
 * The DefaultSelection trait:
 *  * filter the fragments to execute by filtering Examples according to their names
 *  * sorts the Fragments by making sure Steps will be executed before Examples
 */
trait DefaultSelection {
  /** select function returning a filtered and ordered seq of seq of Fragments */
  def select(implicit arguments: Arguments): Fragments => Seq[FragmentSeq] = (fragments: Fragments) => select(fragments.fragments)(arguments)
  /** select function returning a filtered and ordered seq of seq of Fragments from a list of fragments */
  def select(fragments: Seq[Fragment])(implicit arguments: Arguments = Arguments()): Seq[FragmentSeq] = {
    sort(SpecsArguments.filter(fragments)(filter))(arguments)
  }
  /** 
   * the filter method filters examples based on their description,
   * keeping only the ones matching the ex attribute of the arguments object
   */
  protected def filter = (f: Fragment, args: Arguments) => {
    f match {
      case e @ Example(_, _) => e.matches(args.ex)
      case _ => true
    }
  }

  /** 
   * the sort returns sequences of fragments which can be executed concurrently.
   * Among the constraints to respect, any Step Fragment must be executed before any
   * following Example Fragment, so that "first" and "last" actions are executed properly
   * in a Specification
   * 
   * If the arguments specify that the specification is sequential, then
   * each fragment will be executed individually
   */
  protected def sort(fragments: Seq[Fragment])(implicit arguments: Arguments): Seq[FragmentSeq] = {
    if (arguments.sequential) fragments.map(f => FragmentSeq.create(f))
    else isolateSteps(fragments)(arguments).reverse
  }
  
  protected def isolateSteps(fragments: Seq[Fragment])(implicit arguments: Arguments): Seq[FragmentSeq] = {
    fragments.foldLeft(Nil: List[FragmentSeq]) { (res, f) =>
      res match {
        case Nil => List(FragmentSeq.create(f))
        case last :: rest => f match {
          case Step(_) if last.fragments.exists(isExample) => FragmentSeq.create(f) :: last :: rest
          case Example(_, _) if last.fragments.exists(isStep) => FragmentSeq.create(f) :: last :: rest
          case _ => FragmentSeq(last.fragments :+ f) :: rest
        }
      }
    }
  }

}
case class FragmentSeq(fragments: Seq[Fragment]) {
  def arguments = Fragments.create(fragments:_*).arguments
}
case object FragmentSeq {
  def create(f: Fragment) = FragmentSeq(Seq(f))
}
