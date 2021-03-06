package com.radeusgd.archivum.gui.controls.tablecolumns
import cats.implicits._
import com.radeusgd.archivum.gui.EditableView
import com.radeusgd.archivum.gui.controls.{BoundControl, TableControl}
import com.radeusgd.archivum.gui.layout.{LayoutParseError, ParsedLayout}
import com.radeusgd.archivum.gui.utils.XMLUtils
import com.radeusgd.archivum.languages.ViewLanguage
import scalafx.scene

import scala.util.Try
import scala.xml.Node

case class ParsedColumnLayout(node: scene.Node, boundControls: Seq[BoundControl])

class SimpleColumnFactory(override val nodeType: String,
                          make: (String, List[String], EditableView) => scene.Node with BoundControl)
   extends ColumnFactory {
   override def fromXML(xmlnode: Node, ev: EditableView): Either[LayoutParseError, Column] =
      if (xmlnode.child != Nil) Left(LayoutParseError(xmlnode, "This node shouldn't have any children"))
      else {
         val path = XMLUtils.extractPath(xmlnode).getOrElse(Nil)
         val label = xmlnode.attribute(ViewLanguage.Label).map(_.text).getOrElse(path.lastOption.getOrElse(""))
         Right(new SimpleColumn(label, (basePath: List[String], ev: EditableView) => make("", basePath ++ path, ev)))
      }
}
