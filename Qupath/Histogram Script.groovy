import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import qupath.lib.display.ImageDisplay

def imageData = getCurrentImageData()
def server = imageData.getServer()

def imagePixels = new ImageDisplay(imageData)
def channelNames = imagePixels.availableChannels()
def RFPHisto = imagePixels.getHistogram(channelNames[0])
def histoBins = RFPHisto.nBins()
def histoRange = RFPHisto.getEdgeRange()
def histoBinWidth = histoRange/histoBins

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

for(int i = 0;i < histoBins; i++) {
    if(i != 0) {
        differential = RFPHisto.getCountsForBin(i)-RFPHisto.getCountsForBin(i-1)
        if (differential < -150) {
            lastDiff = (histoBinWidth*i)
        }
    }
}
//print("Intensity: "+lastDiff)

def striatum = getAnnotationObjects()
double threshold = lastDiff

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

imageName = getProjectEntry().getImageName()
animalNumber = imageName.substring(0,3)
imageName = imageName.toLowerCase()
folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results')
fileName = buildFilePath(folderName, animalNumber+'_DMi8_Area_Measurements.csv')
roiFileName = buildFilePath(folderName, imageName+'.geojson')

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Hemisphere Side, Background Threshold, Whole Striatum Area (uM^2), RFP + Area (uM^2), Positve Percentage Area\n")
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

striatum.each {str->
    deselectAll()
    getCurrentHierarchy().getSelectionModel().setSelectedObject(str, true)
    hemi = str.getName()
    print("Processing "+hemi+" Hemisphere")
    strArea = str.getROI().getArea()*pixelArea
//    print("Area of Striatum: "+strArea)
    
    // Apply classifier
    createDetectionsFromPixelClassifier(thresholder, 0.0, 0.0)
    rfpAreaDet = str.getChildObjects()[0]
    rfpArea = rfpAreaDet.getROI().getArea()*pixelArea
//    print("RFP + Area in "+hemi+" Striatum: "+rfpArea)
    positiveAreaPercentage = (rfpArea/strArea)*100
//    print("Positive RFP Percentage of "+hemi+" Striatum: "+positiveAreaPercentage)
    print(positiveAreaPercentage+"%, "+rfpArea+"uM^2")
    makeFile([hemi, threshold, strArea, rfpArea, positiveAreaPercentage])
}
makeROIFile()
print("Done!")