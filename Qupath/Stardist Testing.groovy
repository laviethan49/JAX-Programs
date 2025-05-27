import qupath.ext.stardist.StarDist2D

// Specify the model file (you will need to change this!)
var pathModel = 'C:/Users/savile/Desktop/QuPath-0.3.2/scripts/dsb2018_heavy_augment.pb'
var imageData = getCurrentImageData()
selectObjects {
   return it.isDetection()
}
toDelete = getSelectedObjects()
removeObjects(toDelete, true)

section_slide = 'c1_se6_-'
//
//var stardistRFP = StarDist2D.builder(pathModel)
//        .threshold(0.5)              // Prediction threshold
//        .normalizePercentiles(1, 99) // Percentile normalization
//        .channels("RFP")            // Specify detection channel
//        .pixelSize(0.5)              // Resolution for detection
//        .measureShape()              // Add shape measurements
//        .measureIntensity()          // Add cell measurements (in all compartments)
//        .build()
//// Run detection for the selected objects
//selectAnnotations()
//selectObjects {
//   return it.isAnnotation() && it.getPathClass() == getPathClass('RFP') && it.getPathClass() != getPathClass('DAPI')
//}
//var pathObjects = getSelectedObjects()
//stardistRFP.detectObjects(imageData, pathObjects)
//def toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') <= 50}
//removeObjects(toDelete, true)
//toDelete = getDetectionObjects().findAll {measurement(it, 'Circularity') <= 0.6}
//removeObjects(toDelete, true)
//toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') >= 500}
//removeObjects(toDelete, true)
//selectDetections()
//def viewer = getCurrentViewer()
//writeRenderedImage(viewer, 'C:/Users/savile/Desktop/Leong 559 QuPath Project/RFP '+section_slide+'.tif')

var stardistNuen = StarDist2D.builder(pathModel)
        .threshold(0.5)              // Prediction threshold
        .normalizePercentiles(1, 99) // Percentile normalization
        .channels("Nuen")            // Specify detection channel
        .pixelSize(0.5)              // Resolution for detection
        .measureShape()              // Add shape measurements
        .measureIntensity()          // Add cell measurements (in all compartments)
        .build()
selectObjects {
   return it.isAnnotation() && it.getPathClass() == getPathClass('Nuen')
}
pathObjects = getSelectedObjects()
stardistNuen.detectObjects(imageData, pathObjects)
selectObjects {
   return it.isDetection() && it.getPathClass() != getPathClass('RFP')
}
toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') <= 30}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Circularity') <= 0.6}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') >= 500}
removeObjects(toDelete, true)
selectDetections()
viewer = getCurrentViewer()
writeRenderedImage(viewer, 'C:/Users/savile/Desktop/Leong 559 QuPath Project/Nuen '+section_slide+'.tif')

//section_slide = 'c1_se6_-'
//def viewer = getCurrentViewer()
//writeRenderedImage(viewer, 'C:/Users/savile/Desktop/Leong 559 QuPath Project/Colocalized '+section_slide+'.tif')

println 'Done!'