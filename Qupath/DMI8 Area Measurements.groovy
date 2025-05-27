import qupath.lib.display.ImageDisplay
import qupath.lib.roi.RoiTools

def void defineChannelColors() {
    def channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
//    setChannelColors(-65536, -16776961)
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    def channelCount = server.nChannels()
    def int redColor = -65536
    def int greenColor = -16711936
    def int blueColor = -16776961
    def channelOrder = []
    def imageChannel = ""
    def channelColor = ""

    setChannelColors(imageData, redColor, greenColor, blueColor)//DMi8 order
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

def findRFPBackgroundThreshold() {
    def imageData = getCurrentImageData()
    def imagePixels = new ImageDisplay().create(imageData)
    def channelNames = imagePixels.availableChannels()
    def rfpChannel = -1
    def channelName = channelNames[2]
    
    for(int i = 0; i <= channelNames.size()-1; i++) {
        channelName = channelNames[i] as String
        if(channelName.indexOf("RFP") != -1) {
            rfpChannel = i
        }
    }

    def histogram = imagePixels.getHistogram(channelNames[rfpChannel])
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
    
    def peaksLength = peaks.size()-1
    def lastPeak = peaks[peaksLength]
    def bgCutoff = lastPeak+(histogram.getStdDev()*1.5)

    return bgCutoff
}

defineChannelColors()

bgThreshold = findRFPBackgroundThreshold()
print("RFP Background Threshold: "+bgThreshold)
println(createRFPPosAnnotations())

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
    def oldAnnoSize = oldAnnotations.size()

    def annotationNames = []
    def posAreaAnnotations = []

    if(oldAnnoSize != 0) {
        clearDetections()
        oldAnnotations.each {anno->
            annotationName = anno.getName()
            if(annotationName != null) {
                println("Processing "+annotationName+" Annotation...")
                deselectAll()
                getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
                createDetectionsFromPixelClassifier(thresholder, 0.0, 0.0)
                redAnno = anno.getChildObjects()[0]
                redAnno.setName("RFP Pos Area of "+annotationName)
                annoROI = anno.getROI()
                annoArea = annoROI.getArea()*pixelArea

                redROI = redAnno.getROI()
                RoiTools.fillHoles(redROI)
                redArea = redROI.getArea()*pixelArea
                
                positiveAreaPercentage = (redArea/annoArea)*100
        
                print("Positive Area of "+annotationName+": "+positiveAreaPercentage+"%, "+redArea+"uM^2")
                annotationNames.add(annotationName)
                posAreaAnnotations.add(positiveAreaPercentage)
            }
        }
    }

    return([annotationNames, posAreaAnnotations])
}
