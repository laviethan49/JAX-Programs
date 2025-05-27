import qupath.lib.roi.RoiTools
import qupath.lib.roi.RoiTools.CombineOp
imageData = getCurrentImageData()
server = imageData.getServer()
metaData = server.getMetadata()
cal = metaData.getPixelCalibration()

positiveThreshold = 1350

channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
tissueArray = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm","eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin","small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram","testes"] as String []

imageName = getProjectEntry().getImageName().toLowerCase().replace(".tif", "")
folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
fileName = buildFilePath(folderName, imageName+"_BG_threshold_"+positiveThreshold+'.csv')
roiFileName = buildFilePath(folderName, imageName+"_BG_threshold_"+positiveThreshold+'.geojson')

findPositiveCells()

def makeFile(data)
{
    mkdirs(folderName)
    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
        resultsFile.append("Image Name, Background Threshold, DAPI Nuclei Count, tdTomato+Cell Count, tdTomato+ Percentage\n")
    }
    resultsFile.append(imageName)
    data.each {
        resultsFile.append(","+it)
    }
    resultsFile.append("\n")
    exportAllObjectsToGeoJson(roiFileName, "FEATURE_COLLECTION")
}
def findPositiveCells() {
    clearDetections()
    deselectAll()
    annotations = getAnnotationObjects()
    annotations.each {anno->
        //Select current annotation
        getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true);
        //Run cell detection for DAPI channel, creating detections for nuclei
        runPlugin('qupath.imagej.detect.cells.PositiveCellDetection','{"detectionImage":"DAPI","requestedPixelSizeMicrons":0.65,"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":1.5,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":100.0,"watershedPostProcess":true,"cellExpansionMicrons":3.0,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+positiveThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
        //Gets all detections within current annotation
        detections = anno.getChildObjects()
        //From all detections in the current annotation, get the detections that are the RFP areas
        positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
        totalNuclei = detections.size()//Total nuclei for finding % done during process
        positivePercentage = (positiveCellCount/totalNuclei)*100
        makeFile([positiveThreshold, totalNuclei, positiveCellCount, positivePercentage])
        println("Total Nuclei: "+totalNuclei+", Positive Cells: "+positiveCellCount+", Percentage:"+positivePercentage)
        //Deselect everything so the next loop will only focus on the next annotation
        deselectAll()
    }
}
println "Done!"