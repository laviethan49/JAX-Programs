import qupath.lib.display.ImageDisplay
import qupath.lib.analysis.DelaunayTools

def findBackgroundRFP() {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()

    def imagePixels = new ImageDisplay().create(imageData)
    def channelNames = imagePixels.availableChannels()
    def histogram = imagePixels.getHistogram(channelNames[2])
    def histoStdDev = histogram.getStdDev()*1.5

    def cal = server.getPixelCalibration()
    def width = server.getMetadata().getPixelWidthMicrons()
    def height = server.getMetadata().getPixelHeightMicrons()

    def pixelArea = width*height
    def histoBins = histogram.nBins()
    def histoRange = histogram.getEdgeRange()
    def histoBinWidth = histoRange/histoBins
    def appliedVariance = Math.ceil(histoBinWidth)
    def maxPeak = histogram.getMaxCount()
    def variance = histogram.getVariance()
    def stdDev = histogram.getStdDev()
    def maxBin = 0
    def differential = 0
    def lastDiff = 0
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

    def peaksCount = peaks.size()-1
    def bgPeak = peaks[peaksCount]+histoStdDev

    def striatum = getAnnotationObjects()
    double threshold = bgPeak
    
    return threshold
}

def makeRFPDetections() {
    def threshold = findBackgroundRFP()
    def json = """
    {
      "pixel_classifier_type": "OpenCVPixelClassifier",
      "metadata": {
        "inputPadding": 0,
        "inputResolution": {
          "pixelWidth": {
            "value": 0.5,
            "unit": "µm"
          },
          "pixelHeight": {
            "value": 0.5,
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
            """+threshold+"""
          ]
        }
      }
    }
    """

    def thresholder = GsonTools.getInstance().fromJson(json, qupath.lib.classifiers.pixel.PixelClassifier)
    
    annotations = getAnnotationObjects()
    clearDetections()
    deselectAll()
    annotations.each {anno->
        getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true)
        createDetectionsFromPixelClassifier(thresholder, 50.0, 0.0, "SPLIT")
    }
}

def createEncompassedROI() {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()

    def cal = server.getPixelCalibration()
    def width = server.getMetadata().getPixelWidthMicrons()
    def height = server.getMetadata().getPixelHeightMicrons()
    
    def averageCenterXArray = []
    def averageCenterYArray = []
    
    def lowY = server.getHeight() + 1
    def highY = -1
    
    def lowestDet
    def highestDet
    
    makeRFPDetections()
    detections = getDetectionObjects()
    detections.each {det->
        detX = det.getROI().getBoundsX()*width
        detY = det.getROI().getBoundsY()*height
        
        centerROIX = det.getROI().getCentroidX()*width
        centerROIY = det.getROI().getCentroidY()*height
        
        averageCenterXArray.add(centerROIX)
        averageCenterYArray.add(centerROIY)
        if(detY > highY) {
            highY = detY
            highestDet = det
        }
        if(detY < lowY) {
            lowY = detY
            lowestDet = det
        }
    }
    
    def centerX = averageCenterXArray.average()
    def centerY = averageCenterYArray.average()
    
//    deselectAll()
//    getCurrentHierarchy().getSelectionModel().setSelectedObjects([lowestDet, highestDet], highestDet)
    //Make sure the average of all of the detections is in the ROI that is going to be created
    def distanceMicrons = 25
    def distancePixels = distanceMicrons.div(height)
    def numPixels = distancePixels.round(0)
    
    def clusters = new DelaunayTools().newBuilder(detections).calibration(cal).centroids().build().getClusters(DelaunayTools.centroidDistancePredicate(numPixels, true))
    
    println(clusters.size())
}

createEncompassedROI()