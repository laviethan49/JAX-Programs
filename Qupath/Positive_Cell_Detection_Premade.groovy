import qupath.lib.roi.RoiTools
import qupath.lib.roi.RoiTools.CombineOp

imageData = getCurrentImageData()
server = imageData.getServer()
metaData = server.getMetadata()
cal = metaData.getPixelCalibration()
channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
tissueArray = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram", "testes"] as String []
imageName = getProjectEntry().getImageName().toLowerCase()
folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
fileName = buildFilePath(folderName, 'Summary_C01.csv')//imageName+'.csv')
roiFileName = buildFilePath(folderName, imageName+'.geojson')
positiveThreshold = 40
accuracyForPixelSize = 0.25
pixelWidth = server.getMetadata()['pixelWidthMicrons']
pixelHeight = server.getMetadata()['pixelHeightMicrons']
pixelCal = (pixelWidth+pixelHeight)/2
pixelCal = pixelCal/accuracyForPixelSize

pixelCalString = pixelCal+" out of max "+pixelWidth

findPositiveCells()

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, GFP Neuron Count, DAPI Nuclei Count, tdTomato+ Cell Count, tdTomato+ Percentage, ROI Area (um^2), Z-Stack Index, Pixel Accuracy for Cell Detection\n")
    }
    resultsFile.append(imageName)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append("\n")
    
    exportSelectedObjectsToGeoJson(roiFileName, "PRETTY_JSON", "FEATURE_COLLECTION")
}

def findPositiveCells() {
    annotations = getAnnotationObjects()
    clearDetections()
    deselectAll()
    annotations.each {anno->
        //Select current annotation
        getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
        
        //Run cell detection for DAPI channel, creating detections for nuclei
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": '+pixelCal+',  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 10.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}')
        //Gets all detections within current annotation
        detections = anno.getChildObjects()
        totalNuclei = detections.size()//Total nuclei
        
        //Run cell detection for GFP channel, creating detections for nuclei
        runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage":"GFP","requestedPixelSizeMicrons":'+pixelCal+',"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":2,"sigmaMicrons":3,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.10,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+positiveThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
        //Gets all detections within current annotation
        detections = anno.getChildObjects()
        //From all detections in the current annotation, get the detections that are the RFP areas
        positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
        negativeCellCount = detections.findAll {it.getPathClass().toString().contains("Neg")}.size()
        totalNeurons = positiveCellCount+negativeCellCount//Total nuclei for finding % done during process
        
        if(totalNeurons != 0) {
            positivePercentage = (positiveCellCount/totalNeurons)*100
        }
        else {
            positivePercentage = 0
        }
        roiArea = anno.getROI().getArea()*0.1262*0.1262
        currentZ = anno.getROI().getZ()+1
  
        deselectAll()      
        getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
        makeFile([positiveThreshold, totalNeurons, totalNuclei, positiveCellCount, positivePercentage, roiArea, currentZ, pixelCalString])
        
        println("Total Nuerons: "+totalNeurons+", Total Nuclei: "+totalNuclei+", Positive Cells: "+positiveCellCount+", Percentage: "+positivePercentage+", Z-Stack: "+currentZ)
        //Deselect everything so the next loop will only focus on the next annotation
        deselectAll()
    }
}
println "Done"