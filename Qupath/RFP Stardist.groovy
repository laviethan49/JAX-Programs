import qupath.ext.stardist.StarDist2D

// Specify the model file (you will need to change this!)
var pathModel = 'C:/Users/savile/Desktop/QuPath-0.3.2/scripts/dsb2018_heavy_augment.pb'
var imageData = getCurrentImageData()
selectObjects {
   return it.isDetection()
}
toDelete = getSelectedObjects()
removeObjects(toDelete, true)

section_slide = '565_sl1_se3'

var stardistRFP = StarDist2D.builder(pathModel)
        .threshold(0.5)              // Prediction threshold
        .normalizePercentiles(1, 99) // Percentile normalization
        .channels("RFP")            // Specify detection channel
        .pixelSize(0.5)              // Resolution for detection
        .measureShape()              // Add shape measurements
        .measureIntensity()          // Add cell measurements (in all compartments)
        .build()
selectObjects {
   return it.isAnnotation() && it.getPathClass() == getPathClass('RFP')
}
var pathObjects = getSelectedObjects()
stardistRFP.detectObjects(imageData, pathObjects)
def toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') <= 30}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Circularity') <= 0.6}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') >= 500}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Solidity') < 0.99 && measurement(it, 'RFP: Mean') < 30}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'RFP: Mean') < 30}
removeObjects(toDelete, true)
selectDetections()
def viewer = getCurrentViewer()
writeRenderedImage(viewer, 'C:/Users/savile/Desktop/RFP '+section_slide+'.tif')

println 'Done!'