// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.speedy

import com.daml.lf.PureCompiledPackages
import com.daml.lf.data.Ref._
import com.daml.lf.data.{ImmArray, Numeric, Ref}
import com.daml.lf.language.Ast._
import com.daml.lf.language.LanguageVersion
import com.daml.lf.language.Util._
import com.daml.lf.speedy.SError._
import com.daml.lf.speedy.SResult._
import com.daml.lf.testing.parser.Implicits._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.LoggerFactory

import scala.language.implicitConversions

class InterpreterTest extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  private implicit def id(s: String): Ref.Name = Name.assertFromString(s)

  private def runExpr(e: Expr): SValue = {
    val machine = Speedy.Machine.fromPureExpr(PureCompiledPackages.Empty, e)
    machine.run() match {
      case SResultFinalValue(v) => v
      case res => throw new RuntimeException(s"Got unexpected interpretation result $res")
    }
  }

  "evaluator behaves responsibly" should {
    // isolated rendition of the DA.Test.List.concat_test scenario in
    // stdlib, which failed after I introduced FrontQueue. It happened
    // to be a missing reverse in Interp.
    "concat works" in {
      val int64 = TBuiltin(BTInt64)
      val int64List = TApp(TBuiltin(BTList), int64)

      def int64Cons(nums: ImmArray[Long], tail: Expr): Expr =
        ECons(int64, nums.map(i => EPrimLit(PLInt64(i))), tail)

      val int64Nil = ENil(int64)
      val concat =
        EAbs(
          ("xss", TApp(TBuiltin(BTList), int64List)),
          ELet(
            Binding(
              Some("work"),
              TFun(int64List, TFun(int64List, int64List)),
              EAbs(
                ("xs", int64List),
                EAbs(
                  ("acc", int64List),
                  EApp(
                    EApp(
                      EApp(
                        EBuiltin(BFoldr),
                        EAbs(
                          ("x", int64),
                          EAbs(
                            ("accInner", int64List),
                            ECons(int64, ImmArray(EVar("x")), EVar("accInner")),
                            None,
                          ),
                          None,
                        ),
                      ),
                      EVar("acc"),
                    ),
                    EVar("xs"),
                  ),
                  None,
                ),
                None,
              ),
            ),
            EApp(EApp(EApp(EBuiltin(BFoldl), EVar("work")), ENil(int64)), EVar("xss")),
          ),
          None,
        )
      val xss1 = ECons(
        int64List,
        ImmArray(int64Cons(ImmArray(2, 5), int64Nil), int64Cons(ImmArray[Long](7), int64Nil)),
        ENil(int64List),
      )
      val xss2 = ECons(int64List, ImmArray(int64Cons(ImmArray(2, 5, 7), int64Nil)), ENil(int64List))
      runExpr(EApp(concat, xss1)) shouldBe runExpr(EApp(concat, xss2))
    }
  }

  "compilation and evaluation handle properly nat types" in {

    def result(s: String) =
      SValue.SOptional(Some(SValue.SNumeric(Numeric.assertFromString(s))))

    val testCases = Table(
      "input" -> "output",
      e"""(/\ (n: nat). TEXT_TO_NUMERIC @n "0") @1""" ->
        result("0.0"),
      e"""(/\ (n: nat). /\ (n: nat). TEXT_TO_NUMERIC @n "1") @2 @3 """ ->
        result("1.000"),
      e"""(/\ (n: nat). /\ (n: nat). \(n: Text) -> TEXT_TO_NUMERIC @n n) @4 @5 "2"""" ->
        result("2.00000"),
      e"""(/\ (n: nat). \(n: Text) -> /\ (n: nat). TEXT_TO_NUMERIC @n n) @6 "3" @7""" ->
        result("3.0000000"),
      e"""(\(n: Text) -> /\ (n: nat). /\ (n: nat). TEXT_TO_NUMERIC @n n) "4" @8 @9""" ->
        result("4.000000000"),
      e"""(\(n: Text) -> /\ (n: *). /\ (n: nat). TEXT_TO_NUMERIC @n n) "5" @Text @10""" ->
        result("5.0000000000"),
    )

    forEvery(testCases) { (input, output) =>
      runExpr(input) shouldBe output
    }

    a[Compiler.CompilationError] shouldBe thrownBy(
      runExpr(e"""(/\ (n: nat). /\ (n: *). TEXT_TO_NUMERIC @n n) @4 @Text""")
    )
  }

  "large lists" should {
    val t_int64 = TBuiltin(BTInt64)
    val t_int64List = TApp(TBuiltin(BTList), t_int64)
    val list = ECons(
      t_int64List,
      ImmArray((1 to 100000).map(i => EPrimLit(PLInt64(i.toLong)))),
      ENil(t_int64List),
    )
    var machine: Speedy.Machine = null
    "compile" in {
      machine = Speedy.Machine.fromPureExpr(PureCompiledPackages.Empty, list)
    }
    "interpret" in {
      val value = machine.run() match {
        case SResultFinalValue(v) => v
        case res => throw new RuntimeException(s"Got unexpected interpretation result $res")
      }
      value match {
        case SValue.SList(lst) =>
          lst.length shouldBe 100000
          val arr = lst.toImmArray
          arr(0) shouldBe SValue.SInt64(1)
          arr(99999) shouldBe SValue.SInt64(100000)
        case v => sys.error(s"unexpected resulting value $v")

      }
    }
  }

  "tracelog" should {
    val logger = LoggerFactory.getLogger("test-daml-trace-logger")
    "empty size" in {
      val log = RingBufferTraceLog(logger, 10)
      log.iterator.hasNext shouldBe false
    }
    "half full" in {
      val log = RingBufferTraceLog(logger, 2)
      log.add("test", None)
      val iter = log.iterator
      iter.hasNext shouldBe true
      iter.next() shouldBe (("test", None))
      iter.hasNext shouldBe false
    }
    "overflow" in {
      val log = RingBufferTraceLog(logger, 2)
      log.add("test1", None)
      log.add("test2", None)
      log.add("test3", None) // should replace "test1"
      val iter = log.iterator
      iter.hasNext shouldBe true
      iter.next() shouldBe (("test2", None))
      iter.hasNext shouldBe true
      iter.next() shouldBe (("test3", None))
      iter.hasNext shouldBe false
    }
  }

  /** Test that the package reloading works */
  "package reloading" should {
    val dummyPkg = PackageId.assertFromString("dummy")
    val ref = Identifier(dummyPkg, QualifiedName.assertFromString("Foo:bar"))
    val modName = DottedName.assertFromString("Foo")
    val pkgs1 = PureCompiledPackages.Empty
    val pkgs2 =
      PureCompiledPackages.assertBuild(
        Map(
          dummyPkg ->
            Package(
              List(
                Module(
                  name = modName,
                  definitions = Map(
                    DottedName.assertFromString("bar") ->
                      DValue(TBuiltin(BTBool), true, ETrue, false)
                  ),
                  templates = Map.empty,
                  exceptions = Map.empty,
                  featureFlags = FeatureFlags.default,
                )
              ),
              Set.empty[PackageId],
              LanguageVersion.default,
              None,
            )
        )
      )
    val pkgs3 = PureCompiledPackages.assertBuild(
      Map(
        dummyPkg ->
          Package(
            List(
              Module(
                name = modName,
                definitions = Map.empty,
                templates = Map.empty,
                exceptions = Map.empty,
                featureFlags = FeatureFlags.default,
              )
            ),
            Set.empty[PackageId],
            LanguageVersion.default,
            None,
          )
      )
    )

    "succeeds" in {
      val machine = Speedy.Machine.fromPureExpr(pkgs1, EVal(ref))
      val result = machine.run()
      result match {
        case SResultNeedPackage(pkgId, _, cb) =>
          ref.packageId shouldBe pkgId
          cb(pkgs2)
          val result = machine.run()
          result shouldBe SResultFinalValue(SValue.SBool(true))
        case _ =>
          sys.error(s"expected result to be missing definition, got $result")
      }

    }

    "crashes without definition" in {
      val machine = Speedy.Machine.fromPureExpr(pkgs1, EVal(ref))
      val result = machine.run()
      result match {
        case SResultNeedPackage(pkgId, _, cb) =>
          ref.packageId shouldBe pkgId
          try {
            cb(pkgs3)
            sys.error(s"expected crash when definition not provided")
          } catch {
            case _: SErrorCrash => ()
          }
        case _ =>
          sys.error(s"expected result to be missing definition, got $result")
      }

    }

  }

}
