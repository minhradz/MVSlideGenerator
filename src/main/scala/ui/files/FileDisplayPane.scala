package ui.files

import javafx.beans.property.{IntegerProperty, ListProperty, SimpleIntegerProperty, SimpleListProperty}
import javafx.concurrent.Task
import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.scene.text.Font
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.{FileChooser, Stage}
import model.{TextProcessor, TextRenderer}
import ui.{ApplicationScene, Globals}

import scala.collection.JavaConverters._
import scala.io.Source

class FileDisplayPane(labelText: String, val parent: ApplicationScene, allowSplitting: Boolean)(implicit stage: Stage) extends VBox(10.0) {
  // This is the primary property that needs to be visible to the parent scene
  private val _fontSize: IntegerProperty = new SimpleIntegerProperty(0)
  def fontSize: Int = _fontSize.getValue
  private val _lines: ListProperty[String] = new SimpleListProperty[String]()
  def getLines: List[String] = _lines.get().asScala.toList

  // When the font size is changes, we want to wait a bit, then render a new preview (to prevent stuttering renders)
  _fontSize.addListener((_, _, newVal) => {
    // First, whenever our font size changes, start a new task that just waits .5 seconds in the background
    val waitAndUpdateImage = new Task[Unit]() {
      override def call(): Unit = {
        Thread.sleep(500)
      }
    }
    // When those .5 seconds pass, if the value we had previously updated to is still our current font value
    // (i.e. user stopped moving slider), then we render a new preview image
    waitAndUpdateImage.setOnSucceeded(_ => {
      if(newVal.intValue() == this._fontSize.getValue) {
        parent.renderPreviewImage()
      }
    })

    new Thread(waitAndUpdateImage).start()
  })

  // First we do some nice padding to make the labels look nicer
  setPadding(new Insets(0, 10, 0, 10))
  val label: Label = new Label(labelText) {
    setPadding(new Insets(10, 0, 0, 5))
    setFont(Font.font(Globals.uiFont))
  }

  // These are the two components mainly responsible for manipulating the exposed properties
  val fileContents: ListView[String] = new FileContentArea(parent.setPreviewImage)
  _lines.bind(fileContents.itemsProperty())

  val fontSlider: FontSlider = new FontSlider
  _fontSize.bind(fontSlider.fontValue)

  // The row below the list of lines which allows user interaction
  val toolRow: HBox = new HBox(10.0) {
    val button: Button = new Button("Load File") {
      setOnAction(_ => fillTextAreaWithFileContents)
      setFont(Font.font(Globals.uiFont))
    }

    setAlignment(Pos.CENTER)
    getChildren.addAll(fontSlider, button)
  }
  HBox.setHgrow(fontSlider, Priority.ALWAYS)

  getChildren.addAll(label, fileContents, toolRow)

  def fillTextAreaWithFileContents: Unit = {
    val fc = new FileChooser
    fc.setTitle("Choose a lyric file.")
    fc.getExtensionFilters.add(
      new ExtensionFilter("All Files", "*.*")
    )
    val chosenFile = fc.showOpenDialog(stage)
    if(chosenFile != null) {
      val lines = Source.fromFile(chosenFile, "UTF-8").getLines().toList
      fileContents.getItems.setAll(lines: _*)
      parent.tryLinkingScrolls()
      val maxFontSize = TextRenderer.getLargestFontSize(getLines.map(TextProcessor.partitionLinesAndReadings(_)._1), parent.rc, allowSplitting)
      fontSlider.updateMaxSize(maxFontSize)
      fontSlider.updateFont(maxFontSize)
    }
  }

  def reset(): Unit = {
    fileContents.getItems.clear()
    fontSlider.updateMaxSize(0)
    fontSlider.updateFont(0)
  }

  def getNthLine(n: Int): Option[String] = {
    // Highlight the line being queried (so the user can see which lines are being rendered)
    fileContents.getSelectionModel.select(n)
    getLines.lift(n)
  }

  def getScrollbar: Option[ScrollBar] = {
    fileContents.lookup(".scroll-bar") match {
      case sc: ScrollBar => Some(sc)
      case _ => None
    }
  }
}