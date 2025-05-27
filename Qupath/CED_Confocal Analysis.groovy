imageData = getCurrentImageData()
server = imageData.getServer()
viewer = getCurrentViewer()

clearAllObjects()
runStack(server.nZSlices())

def runStack(int zSlices) {
    for(int i = 0; i < zSlices; i++) {
        viewer.setZPosition(i)
        height = server.getHeight()//uM
        width = server.getWidth()//uM
        
        frame = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0,0, width, height, ImagePlane.getPlane(i,0)))
        addObject(frame)
        getCurrentHierarchy().getSelectionModel().setSelectedObject(frame, true)
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage":"GFP","requestedPixelSizeMicrons":0.65,"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":2,"sigmaMicrons":3.0,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":3.0,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true}')
        deselectAll()
        getCurrentHierarchy().getSelectionModel().setSelectedObject(frame, true)
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}')
        deselectAll()
    }
}