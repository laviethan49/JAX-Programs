import qupath.lib.display.ImageDisplay
import qupath.lib.roi.ShapeSimplifier

imageName = getProjectEntry().getImageName()
println("Processing: "+imageName)

labName = imageName.substring(0,imageName.indexOf("_"))
animalNumber = imageName.substring(labName.length()+1, labName.length()+4)
imageName = imageName.toLowerCase()
folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results Expanded')
fileName = buildFilePath(folderName, labName+" "+animalNumber+'_DMi8_Area_Measurements_Expanded.csv')
roiFileName = buildFilePath(folderName, imageName+'_Expanded.geojson')

defineChannelColors()
imageData = getCurrentImageData()
server = imageData.getServer()
imagePixels = new ImageDisplay().create(imageData)
imageName = getProjectEntry().getImageName()
accuracyForPixelSize = 0.25
pixelWidth = server.getMetadata()['pixelWidthMicrons']
pixelHeight = server.getMetadata()['pixelHeightMicrons']
pixelCal = (pixelWidth+pixelHeight)/2
pixelScale = pixelCal
pixelCal = pixelCal/accuracyForPixelSize
def channelNames = imagePixels.availableChannels()
RFPHisto = imagePixels.getHistogram(channelNames[2])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP
GFPHisto = imagePixels.getHistogram(channelNames[1])
bgThreshold = findBackground(RFPHisto, 2)[2]
bgThresholdGFP = findBackground(GFPHisto, 2)[2]

print("RFP Background Threshold: "+bgThreshold)
print("GFP Background Threshold: "+bgThresholdGFP)
annoResults = createRFPPosAnnotations()
//makeFile(annoResults)

def void defineChannelColors() {
    def channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    def channelCount = server.nChannels()
    def int redColor = -65536
    def int greenColor = -16711936
    def int blueColor = -16776961
    def channelOrder = []
    def imageChannel = ""
    def channelColor = ""

    setChannelColors(imageData, blueColor, greenColor,redColor)//DMi8 filter cube order
    for(i = 0; i < channelCount; i++)
    {
        imageChannel = server.getChannel(i)
        channelColor = imageChannel.getColor()
        
        switch (channelColor) {
            case redColor:
                channelOrder[i] = "Red"
                break
            case greenColor:
                channelOrder[i] = "Green"
                break
            case blueColor:
                channelOrder[i] = "Blue"
                break
            default:
                println "Color not in library"
                break
        }
    }

    channelOrder.eachWithIndex {value,key ->
        switch (key) {
            case 0:
                setChannelNames(imageData, channelColorsArray[channelOrder[key]])
                break
            case 1:
                setChannelNames(imageData, null, channelColorsArray[channelOrder[key]])
                break
            case 2:
                setChannelNames(imageData, null, null, channelColorsArray[channelOrder[key]])
                break
            default:
                break
        }
    }
}

def findBackground(histogram, peakCount) {
    //Get values from histogra to be used later
    def histoBins = histogram.nBins()
    def highestSignal = histogram.getEdgeMax()
    def scaleBins = Math.ceil(highestSignal / histoBins)
    def stdDev = histogram.getStdDev()/scaleBins
    def baseLine = histogram.getMeanValue()
    //Initialize values
    def peaks = []
    def bins = []
    //Iterate x times, where x is the input number above
    for(int j = 0; j < peakCount; j++) {
        //Initialize more values
        currentPeak = 0
        currentBin = 0
        //Iterate over all bins in histogram, except 0 which is true black
        for(int i = 1; i < histoBins-1; i++) {
            //Current intensity value for bin
            currentBinVal = histogram.getCountsForBin(i)
            //If the for loop has iterated once, check if the peak it found already is skipped over
            //So there are no duplicates. 1 sd from center is used as the threshold
            if(bins.size() > 0) {
                for(int k = 0; k < bins.size(); k++) {
                    lowerLim = bins[k-1] - (stdDev*0.5)
                    upperLim = bins[k-1] + (stdDev*0.5)
                    if(i >= lowerLim && i <= upperLim) {
                        i = i + (int)stdDev
//                        print(lowerLim+" "+upperLim)
                    }
                }
            }
            //If this is the highest peak, assign it's value as such
            if(currentBinVal > currentPeak && currentBinVal !in peaks && currentBinVal > baseLine) {
                currentPeak = currentBinVal
                currentBin = i
            }
        }
        //TODO
        //If peak is highest value possible, most likely the graph shows the bg peaks being very close together
        //Therefore, find the dip, and take the peak after the dip (lowest value between two peaks)
        peaks.add(currentPeak)
        bins.add(currentBin)
    }
    //Multiply bin index value by the scaling for n bins to find actual index for threshold
    bins.eachWithIndex {value, key ->
        bins[key] = value*scaleBins
    }

    bgCutoff = bins[bins.size()-1]+stdDev*2
    bins.add(bgCutoff)
    
    return bins
}

def createRFPPosAnnotations() {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()

    def cal = server.getPixelCalibration()
    def width = server.getMetadata().getPixelWidthMicrons()
    def height = server.getMetadata().getPixelHeightMicrons()

    def pixelArea = width*height
    
    def scalingFactor = 2
    
    def widthScale = width*scalingFactor
    def heightScale = height*scalingFactor
    
    println("Scaling factor width: "+widthScale+", height: "+heightScale)
    
    def json = """
    {
      "pixel_classifier_type": "OpenCVPixelClassifier",
      "metadata": {
        "inputPadding": 0,
        "inputResolution": {
          "pixelWidth": {
            "value": """+widthScale+""",
            "unit": "µm"
          },
          "pixelHeight": {
            "value": """+heightScale+""",
            "unit": "µm"
          },
          "zSpacing": {
            "value": 1.0,
            "unit": "z-slice"
          },
          "timeUnit": "SECONDS",
          "timepoints": []
        },
        "inputWidth": 512,
        "inputHeight": 512,
        "inputNumChannels": 3,
        "outputType": "CLASSIFICATION",
        "outputChannels": [],
        "classificationLabels": {
          "0": {
            "name": "Ignore*",
            "color": [
              180,
              180,
              180
            ]
          },
          "1": {
            "name": "RFP",
            "color": [
              255,
              0,
              0
            ]
          }
        }
      },
      "op": {
        "type": "data.op.channels",
        "colorTransforms": [
          {
            "channelName": "RFP"
          }
        ],
        "op": {
          "type": "op.threshold.constant",
          "thresholds": [
            """+bgThreshold+"""
          ]
        }
      }
    }
    """
    
    def thresholder = GsonTools.getInstance().fromJson(json, qupath.lib.classifiers.pixel.PixelClassifier)
    
    def anno = getAnnotationObjects()[0]
    
    getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
    anno.clearChildObjects()
    println("Processing Annotation...")
    //Inserted SP8 stuff
    //DAPI/ All Cells
    runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": '+pixelCal+',  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 10.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+bgThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
    detections = anno.getChildObjects()
    positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
    negativeCellCount = detections.findAll {it.getPathClass().toString().contains("Neg")}.size()
    possibleNeuronsStriatum = detections.findAll {it.getMeasurements()['Cell: GFP mean'] > bgThresholdGFP && it.getPathClass().toString().contains("Pos")}.size()
    totalCellsStriatum = positiveCellCount+negativeCellCount
    clearDetections()
    //GFP/ Neurons
    runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage":"GFP","requestedPixelSizeMicrons":'+pixelCal+',"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":2,"sigmaMicrons":3,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.10,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+bgThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
    detections = anno.getChildObjects()
    positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
    negativeCellCount = detections.findAll {it.getPathClass().toString().contains("Neg")}.size()
    totalNeuronsStriatum = positiveCellCount+negativeCellCount
    //
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
    plane = getCurrentViewer().getImagePlane()
    
    positiveCells = detections.findAll {it.getPathClass().toString().contains("Pos")}
    
    neuronsROI = []
    positiveCells.each { neuron->
        neuronROI = neuron.getROI()
        neuronsROI.add(neuronROI)
    }
    neuronRoi = RoiTools.union(neuronsROI)
    
    simpleROI = ShapeSimplifier.simplifyShape(neuronRoi, 5.00)
    simplifiedPoints = simpleROI.getAllPoints()

    listCircles = []
    scaledEllipse = 200/((width+height)/2)

    simplifiedPoints.each {point->
        xPoint = point.getX()
        yPoint = point.getY()
        circleRoi = ROIs.createEllipseROI(xPoint-(scaledEllipse/2), yPoint-(scaledEllipse/2), scaledEllipse, scaledEllipse, plane)
        listCircles.add(circleRoi)
    }
    listCircles.add(neuronRoi)
    
    combinedRoi = RoiTools.union(listCircles)
    combinedRoi = RoiTools.fillHoles(combinedRoi)
    
    expandedAnno = PathObjects.createAnnotationObject(combinedRoi)
    expandedAnno.setName("expandedAnno")
    addObject(expandedAnno)
    
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(expandedAnno, true)
    runPlugin('qupath.lib.plugins.objects.DilateAnnotationPlugin', '{"radiusMicrons":-100.0,"lineCap":"ROUND","removeInterior":false,"constrainToParent":false}')
//    removeObject(expandedAnno, true)
    expandedAnno.setName("")
    
    getAnnotationObjects().each {anno2->
        if(anno2.getName() != null) {
            if(anno2.getName().contains("expandedAnno")) {
               shrunkAnno = anno2
            }
        }
    }
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(shrunkAnno, true)

    shrunkROI = shrunkAnno.getROI()
    newROIs = RoiTools.splitROI(shrunkROI)
    biggestArea = 0.0
    newROIs.each {roi->
        roiArea = roi.getArea()
        if(biggestArea < roiArea) {
            currentROI = roi
            biggestArea = roiArea
        }
    }
    currentROI = RoiTools.fillHoles(currentROI)
    removeObject(shrunkAnno, false)
    shrunkAnno = PathObjects.createAnnotationObject(currentROI)
    areaROI = shrunkAnno.getROI().getArea()*width*height

    addObject(shrunkAnno)
    anno.addChildObject(shrunkAnno)
    fireHierarchyUpdate()
    
    println("DAPI Nuclei: "+totalCellsStriatum+" Neun Neurons: "+totalNeuronsStriatum+" DAPI+Neun Cells: "+possibleNeuronsStriatum)
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(shrunkAnno, true)
    //DAPI/ All Cells
    clearDetections()
    runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": '+pixelCal+',  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 10.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+bgThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
    detections = shrunkAnno.getChildObjects()
    positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
    positiveDAPICount = positiveCellCount
    negativeCellCount = detections.findAll {it.getPathClass().toString().contains("Neg")}.size()
    negativeDAPICount = negativeCellCount
    possibleNeurons = detections.findAll {it.getMeasurements()['Cell: GFP mean'] > bgThresholdGFP && it.getPathClass().toString().contains("Pos")}.size()
    totalCells = positiveCellCount+negativeCellCount
    clearDetections()
    //GFP/ Neurons
    runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage":"GFP","requestedPixelSizeMicrons":'+pixelCal+',"backgroundRadiusMicrons":20.0,"backgroundByReconstruction":true,"medianRadiusMicrons":2,"sigmaMicrons":3,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.10,"includeNuclei":false,"smoothBoundaries":true,"makeMeasurements":true,"thresholdCompartment":"Cell: RFP mean","thresholdPositive1":'+bgThreshold+',"thresholdPositive2":0.0,"thresholdPositive3":0.0,"singleThreshold":true}')
    detections = shrunkAnno.getChildObjects()
    positiveCellCount = detections.findAll {it.getPathClass().toString().contains("Pos")}.size()
    positiveNeuronCount = positiveCellCount
    negativeCellCount = detections.findAll {it.getPathClass().toString().contains("Neg")}.size()
    negativeNeuronCount = negativeCellCount
    totalNeurons = positiveCellCount+negativeCellCount
    
    ppDAPI = (positiveDAPICount/totalCells)*100
    ppNeurons = (positiveNeuronCount/totalNeurons)*100
    
    println("Positive DAPI Cells: "+positiveDAPICount+" Positive Neuron Count: "+positiveNeuronCount+" Positive Percentage DAPI: "+ppDAPI+"% Positive Percentage Neurons: "+ppNeurons+"% Area ROI: "+areaROI)

    return([totalCells, totalNeurons, positiveDAPICount, positiveNeuronCount, ppDAPI, ppNeurons, areaROI])
}

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, Total Nuclei, Total Neurons, Pos. DAPI Cells, Pos. Neurons, Pos. % DAPI, Pos. % Neurons, Area ROI\n")
    }
    resultsFile.append(imageName)
    resultsFile.append(",")
    resultsFile.append(bgThreshold)
    data.each {
       resultsFile.append(","+it)
    }
    
    exportSelectedObjectsToGeoJson(roiFileName, "PRETTY_JSON", "FEATURE_COLLECTION")
}
println("\nDone\n")