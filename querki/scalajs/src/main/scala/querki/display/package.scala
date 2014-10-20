package querki

import org.scalajs.dom

import querki.globals._
import querki.pages.ParamMap

package object display {
  trait PageManager extends EcologyInterface {
    /**
     * Actually render the page, inside the given root.
     */
    def setRoot(windowIn:dom.Window, root:dom.Element):Unit
    
    /**
     * Set the folder where images are kept.
     */
    def setImagePath(path:String):Unit
    
    /**
     * The URL path to get to the system images.
     */
    def imagePath:String
    
    /**
     * Update the current Page's display. This is called after the Page fetches its contents.
     */
    def update(title:String):Unit
    
    /**
     * Switch to the specified page. This is fairly low-level; use higher-level APIs when possible.
     */
    def showPage(pageName:String, paramMap:ParamMap)
  }
  
  trait StatusLine extends EcologyInterface {
    /**
     * Display the given message for a few seconds.
     */
    def showBriefly(msg:String):Unit
    
    /**
     * Display the given message until there is another show.
     */
    def showUntilChange(msg:String):Unit
  }
}
