

imageData = getCurrentImageData()
server = imageData.getServer()
annotations = getAnnotationObjects()
annoROI = annotations[0].getROI()
currentZ = annoROI.getZ()
annoROI = RoiTools.getShape(annoROI)

if(server.nZSlices() > 0) {
   0.upto(server.nZSlices()-1) {
      if(it != currentZ) {
          frame = PathObjects.createAnnotationObject(ROIs.createAreaROI(annoROI, ImagePlane.getPlane(it,0)));
          addObject(frame)
      }
   }
}

println "Done!"