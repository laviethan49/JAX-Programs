import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import qupath.lib.display.ImageDisplay

def imageData = getCurrentImageData()
def server = imageData.getServer()

def imagePixels = new ImageDisplay(imageData)
def channelNames = imagePixels.availableChannels()
def RFPHisto = imagePixels.getHistogram(channelNames[0])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP
def histoStdDev = RFPHisto.getStdDev()*1.5

def differential = 0
def lastDiff = 0

//Create Thresholder - Define parameters
double downsample = 1
int channel = 0
def above = getPathClass('RFP')
def below = getPathClass('Ignore*')

// Figure out the resolution from the current image
def cal = server.getPixelCalibration()
def width = server.getMetadata().getPixelWidthMicrons()
def height = server.getMetadata().getPixelHeightMicrons()

def pixelArea = width*height

def resolution = cal.createScaledInstance(downsample, downsample)

imageName = getProjectEntry().getImageName()
animalNumber = imageName.substring(0,3)
imageName = imageName.toLowerCase()
folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results')
fileName = buildFilePath(folderName, animalNumber+'_DMi8_Area_Measurements.csv')
roiFileName = buildFilePath(folderName, imageName+'.geojson')

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
print(imageName)
def peaks = findPeaks(RFPHisto)
print("Peaks: "+peaks)
def peaksCount = peaks.size()-1
def bgPeak = peaks[peaksCount]+histoStdDev

print("Intensity: "+bgPeak)

double threshold = bgPeak

def json = """
{
  "pixel_classifier_type": "OpenCVPixelClassifier",
  "metadata": {
    "inputPadding": 0,
    "inputResolution": {
      "pixelWidth": {
        "value": 0.6500002275000796,
        "unit": "µm"
      },
      "pixelHeight": {
        "value": 0.6500002275000796,
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

double minArea = 0
double minHoleArea = 0

def thresholder = GsonTools.getInstance().fromJson(json, qupath.lib.classifiers.pixel.PixelClassifier.class)
clearDetections()

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, Whole Striatum Area (uM^2), RFP + Area (uM^2), Positve Percentage Area\n")
    }
    resultsFile.append(imageName)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append("\n")
}

def makeROIFile()
{
    selectAllObjects()
    exportSelectedObjectsToGeoJson(roiFileName, "PRETTY_JSON", "FEATURE_COLLECTION")
}

def striatum = getAnnotationObjects()
striatum.each {str->
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(str, true)
    
    strArea = str.getROI().getArea()*pixelArea
    
    // Apply classifier
    createDetectionsFromPixelClassifier(thresholder, 0.0, 0.0)
    rfpAreaDet = str.getChildObjects()[0]
    rfpArea = rfpAreaDet.getROI().getArea()*pixelArea
    positiveAreaPercentage = (rfpArea/strArea)*100
    print("Positive Area of Striatum: "+positiveAreaPercentage+"%, "+rfpArea+"uM^2")
    makeFile([threshold, strArea, rfpArea, positiveAreaPercentage])
}
makeROIFile()
print("Done!")
print("\n")