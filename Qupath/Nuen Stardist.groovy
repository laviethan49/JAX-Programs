import qupath.ext.stardist.StarDist2D

// Specify the model file (you will need to change this!)
var pathModel = 'C:/Users/savile/Desktop/QuPath-0.3.2/scripts/dsb2018_heavy_augment.pb'
var imageData = getCurrentImageData()
selectObjects {
   return it.isDetection()
}
toDelete = getSelectedObjects()
removeObjects(toDelete, true)

section_slide = '564_sl1_se3'

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
toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') <= 30}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Circularity') <= 0.6}
removeObjects(toDelete, true)
toDelete = getDetectionObjects().findAll {measurement(it, 'Area µm^2') >= 500}
removeObjects(toDelete, true)
selectDetections()
viewer = getCurrentViewer()
writeRenderedImage(viewer, 'C:/Users/savile/Desktop/Nuen '+section_slide+'.tif')

println 'Done!'