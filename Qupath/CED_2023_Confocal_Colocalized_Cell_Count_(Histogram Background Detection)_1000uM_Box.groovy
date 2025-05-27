import qupath.lib.roi.RoiTools
import qupath.lib.roi.RoiTools.CombineOp
import qupath.lib.display.ImageDisplay
import qupath.lib.roi.RectangleROI
import qupath.lib.objects.PathAnnotationObject

imageData = getCurrentImageData()
server = imageData.getServer()
allZ = server.nZSlices()
middleZ = Math.ceil((allZ)/2)
metaData = server.getMetadata()
cal = metaData.getPixelCalibration()

channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
tissueArray = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram", "testes"] as String []

def imagePixels = new ImageDisplay(imageData)
def channelNames = imagePixels.availableChannels()
def RFPHisto = imagePixels.getHistogram(channelNames[2])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP

imageName = getProjectEntry().getImageName()
labName = imageName.substring(0,imageName.indexOf("_"))
animalNumber = imageName.substring(labName.length()+1, labName.length()+4)
imageName = imageName.toLowerCase()
folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results')
fileName = buildFilePath(folderName, labName+" "+animalNumber+'_Confocal_Analysis_Box.csv')
roiFileName = buildFilePath(folderName, imageName+'.geojson')

def peaks = findPeaks(RFPHisto)
//println("Peaks: "+peaks)
def peaksLength = peaks.size()-1
def lastPeak = peaks[peaksLength]
positiveThreshold = lastPeak+(RFPHisto.getStdDev()*1.5)
print(positiveThreshold)

def findPeaks(histogram) {
    def histoBins = histogram.nBins()
    def histoRange = histogram.getEdgeRange()
    def histoBinWidth = histoRange/histoBins
    def appliedVariance = Math.ceil(histoBinWidth)
    def maxPeak = histogram.getMaxCount()
    def variance = histogram.getVariance()
    def stdDev = histogram.getStdDev()
    def maxBin = 0
    def differential = 0
    def biggestDiff = 0
    def peakBin = 0
    def peaks = []
    
    for(int i = 5; i < histoBins; i++) {
        differential = histogram.getCountsForBin(i)-histogram.getCountsForBin(i-1)
        if(biggestDiff > differential) {
            biggestDiff = differential
            peakBin = (histoBinWidth*i)
        }
    }
    peaks.add(peakBin)
    
    def initialBGPeak = (peakBin/histoBinWidth)+10
    differential = 0
    biggestDiff = 0

    for(int i = initialBGPeak; i < histoBins; i++) {
        differential = histogram.getCountsForBin(i)-histogram.getCountsForBin(i-1)
        if(biggestDiff > differential) {
            biggestDiff = differential
            peakBin = (histoBinWidth*i)
        }
    }
    peaks.add(peakBin)

    return peaks
}

accuracyForPixelSize = 0.25
pixelWidth = server.getMetadata()['pixelWidthMicrons']
pixelHeight = server.getMetadata()['pixelHeightMicrons']
pixelCal = (pixelWidth+pixelHeight)/2
pixelScale = pixelCal
pixelCal = pixelCal/accuracyForPixelSize

pixelCalString = pixelCal+" out of max "+pixelWidth

findPositiveCells()

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, GFP Neuron Count, DAPI Nuclei Count, tdTomato+ Neuron Count, tdTomato+ Percentage, ROI Area (um^2), Z-Stack Index, Pixel Accuracy for Cell Detection\n")
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
    // Size in pixels at the base resolution
    int size = 1000/pixelScale
    
    // Get center pixel
    def viewer = getCurrentViewer()
    int cx = viewer.getCenterPixelX()
    int cy = viewer.getCenterPixelY()

    // Create & add annotation
    def roi = new RectangleROI(cx-size/2, cy-size/2, size, size, getCurrentViewer().getImagePlane())
    def rgb = getColorRGB(50, 50, 200)
    def pathClass = getPathClass('Other', rgb)
    def annotation = new PathAnnotationObject(roi, pathClass)
    addObject(annotation)
//    annotations.each {anno->
        currentZ = annotation.getROI().getZ()+1
//        if(middleZ == currentZ) {
            //Select current annotation
            getCurrentHierarchy().getSelectionModel().setSelectedObject(annotation, true)
        
            //Run cell detection for DAPI channel, creating detections for nuclei
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": '+pixelCal+',  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 10.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}')
            //Gets all detections within current annotation
            detections = annotation.getChildObjects()
            totalNuclei = detections.size()//Total nuclei
        
            //Run cell detection for GFP channel, creating detections for nuclei
            runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage":"GFP","requestedPixelSizeMicrons":'+pixelCal+',"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":2,"sigmaMicrons":3,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.10,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+positiveThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
            //Gets all detections within current annotation
            detections = annotation.getChildObjects()
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
            roiArea = annotation.getROI().getArea()*pixelScale*pixelScale
  
            deselectAll()      
            getCurrentHierarchy().getSelectionModel().setSelectedObject(annotation, true)
            makeFile([positiveThreshold, totalNeurons, totalNuclei, positiveCellCount, positivePercentage, roiArea, currentZ, pixelCalString])
            
            println(imageName)
            println("Background Intensity: "+positiveThreshold+" Total Nuerons: "+totalNeurons+", Total Nuclei: "+totalNuclei+", Positive Cells: "+positiveCellCount+", Percentage: "+positivePercentage+", Z-Stack: "+currentZ)
            //Deselect everything so the next loop will only focus on the next annotation
            deselectAll()
//        }
//    }
}
println "Done"