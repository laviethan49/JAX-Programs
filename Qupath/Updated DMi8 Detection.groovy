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
imagePixels = new ImageDisplay().create(imageData)
imageName = getProjectEntry().getImageName()
def channelNames = imagePixels.availableChannels()
RFPHisto = imagePixels.getHistogram(channelNames[0])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP
bgThreshold = findBackground(RFPHisto, 2)[2]
print("RFP Background Threshold: "+bgThreshold)
annoResults = createRFPPosAnnotations()
makeFile(annoResults)

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

    setChannelColors(imageData, redColor, greenColor, blueColor)//DMi8 filter cube order
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
    
    def oldAnnotations = getAnnotationObjects()
    def realAnnotations = []
    oldAnnotations.each {value->
        if(value.getName() != null) {
            if(value.getName().toLowerCase() == "right" || value.getName().toLowerCase() == "left") {
                realAnnotations.add(value)
            }
        }
    }
    oldAnnotations = realAnnotations
    def oldAnnoSize = oldAnnotations.size()

    def annotationNames = []
    def posAreaAnnotations = []

    if(oldAnnoSize != 0) {
        oldAnnotations.each {anno->
            annotationName = anno.getName()
            anno.clearChildObjects()
            if(annotationName != null) {
                println("Processing "+annotationName+" Annotation...")
                deselectAll()
                getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
                createAnnotationsFromPixelClassifier(thresholder, 0.0, 0.0)
                
                if(anno.getChildObjects().size() > 0) {
                    plane = getCurrentViewer().getImagePlane()
                    
                    redAnno = anno.getChildObjects()[0]
                    redAnnoROI = redAnno.getROI()
                    redAnno.setName("RFP Pos Area of "+annotationName)
                    simpleROI = ShapeSimplifier.simplifyShape(redAnnoROI, 5.00)
                    simplifiedPoints = simpleROI.getAllPoints()

                    listCircles = []
                    scaledEllipse = 100/((width+height)/2)
                    
                    simplifiedPoints.each {point->
                        xPoint = point.getX()
                        yPoint = point.getY()
                        circleRoi = ROIs.createEllipseROI(xPoint-(scaledEllipse/2), yPoint-(scaledEllipse/2), scaledEllipse, scaledEllipse, plane)
                        listCircles.add(circleRoi)
                    }
                    
                    listCircles.add(redAnnoROI)
                    checkName = "Simple RFP Pos Area of "+annotationName
                    combinedRoi = RoiTools.union(listCircles)
                    combinedRoi = RoiTools.fillHoles(combinedRoi)
                    expandedAnno = PathObjects.createAnnotationObject(combinedRoi)
                    expandedAnno.setName(checkName)
                    addObject(expandedAnno)
                    
                    redAnno.setName("RFP Pos Area of "+annotationName+" Original")
                    deselectAll()
                    getCurrentHierarchy().getSelectionModel().setSelectedObject(expandedAnno, true)
                    runPlugin('qupath.lib.plugins.objects.DilateAnnotationPlugin', '{"radiusMicrons":-50.0,"lineCap":"ROUND","removeInterior":false,"constrainToParent":false}')
    
                    removeObject(expandedAnno, true)
                    getAnnotationObjects().each {anno2->
                        if(anno2.getName() != null) {
                            if(anno2.getName().contains(checkName)) {
                               shrunkAnno = anno2
                            }
                        }
                    }
                    getCurrentHierarchy().getSelectionModel().setSelectedObject(shrunkAnno, true)
                    shrunkAnno.setName("RFP Pos Area of "+annotationName+" Expanded")
    
                    annoROI = shrunkAnno.getROI()
                    newROIs = RoiTools.splitROI(annoROI)
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
                    shrunkAnno.setName("RFP Pos Area of "+annotationName+" Expanded")
                    addObject(shrunkAnno)
                    
                    annoArea = anno.getROI().getArea()*pixelArea
                    redArea = shrunkAnno.getROI().getArea()*pixelArea
                    positiveAreaPercentage = (redArea/annoArea)*100
            
                    print("Positive Area of "+annotationName+": "+positiveAreaPercentage+"%, "+redArea+"uM^2")
                    annotationNames.add(annotationName)
                    posAreaAnnotations.add(positiveAreaPercentage)

                    anno.addChildObject(redAnno)
                    anno.addChildObject(shrunkAnno)
                    fireHierarchyUpdate()
                } else {
                    print("Positive Area of "+annotationName+": 0%, 0 uM^2")
                    annotationNames.add(annotationName)
                    posAreaAnnotations.add(0)
                }
            }
        }
    }
    
    returnedArray = []
    z = 0
    annotationNames.each {name->
        returnedArray.add(name)
        returnedArray.add(posAreaAnnotations[z])
        z = z+1
    }

    return(returnedArray)
}

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, Side, Area %, Side, Area %\n")
    }
    resultsFile.append(imageName)
    resultsFile.append(",")
    resultsFile.append(bgThreshold)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append(",75 um expansion\n")
    
    exportSelectedObjectsToGeoJson(roiFileName, "PRETTY_JSON", "FEATURE_COLLECTION")
}
println("\nDone\n")