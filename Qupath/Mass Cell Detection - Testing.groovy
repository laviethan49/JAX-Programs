def imageData = getCurrentImageData()
def server = imageData.getServer()
def xdist = server.getWidth()
def ydist = server.getHeight()
def tissueArray = ["adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph node", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus"] as String []
def name = getProjectEntry().getImageName()
def nameIndex = -1
def tissueName = ""
def channelCount = server.nChannels()
def viewer = getCurrentViewer()
def properties = imageData.getProperties()
def imageDisplay = viewer.getImageDisplay()
def availableChannels = imageDisplay.availableChannels()
def channelList = viewer.getImageDisplay().availableChannels()
def rfpMean = 0
def rfpMeanIt = 0.0
def rfpCells = 0
//Needs to be changed everytime
def filename = 'C:/Users/savile/Desktop/Bankiewicz Off Target Male Negative Control/detection results/negative_control_results.csv'
File file = new File(filename)
def lines = file.readLines()

/*
availableChannels.each
{
    imageDisplay.setMinMaxDisplay(it, 0, 4065)
}
*/

if (channelCount == 2)
{
    setChannelNames(imageData, "RFP", "DAPI")
}
else
{
    setChannelNames(imageData, "RFP", "GFP", "DAPI")
}

tissueArray.each
{
    nameIndex = name.indexOf(it)
    if(nameIndex != -1)
    {
        tissueName = it
    }
}
print tissueName
for (line in lines) {
    if(line.toLowerCase().indexOf(tissueName) > 0)
    {
        rfpBeginIndex = line.indexOf(',')+1
        rfpString = line.substring(rfpBeginIndex)
        rfpEndIndex = rfpString.indexOf(',')
        rfpString = line.substring(rfpBeginIndex, rfpBeginIndex + rfpEndIndex)
        
        rfpMean = rfpString as double
        rfpMean = Math.ceil((rfpMean/100))*100
    }
}

clearAllObjects()

if (server.nZSlices() >0){
    0.upto(server.nZSlices()-1){
        frame = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0,0,xdist,ydist,ImagePlane.getPlane(it,0)));
        addObject(frame);
    }
}

selectAnnotations()
//'+i+'
for(i = 0; i <= 5; i = i + .5) {
switch(tissueName) {
    case "adrenal":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "bladder":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "brain":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "diaphragm":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "eye":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "gastrocnemius":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "heart":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "kidney":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "large":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "liver":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "lung":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "lymph node":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "pancreas":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "skin":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "small":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "spleen":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "stomach":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "thymus":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "epididymis":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "ovary":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "testis":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    case "uterus":
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": '+i+',  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
    default:
        runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5.0,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
        break;
}

detections = getDetectionObjectsAsArray()

detections.eachWithIndex{it,index->
    rfpMeanIt = measurement(it, "Cell: RFP mean")
    if(rfpMeanIt > rfpMean)
    {
        rfpCells = rfpCells + 1
    }
}

//if(tissueName == "")
//{
//    tissueName = name
//}
//else if(tissueName == "large" || tissueName == "small")
//{
//    tissueName = tissueName + " intestine"
//}
//else if(tissueName == "adrenal")
//{
//    tissueName = tissueName + " gland"
//}
//else if(tissueName == "lymph")
//{
//    tissueName = tissueName + " node"
//}
//else if(tissueName == "gastrocnemius")
//{
//    tissueName = tissueName + " muscle"
//}
//
//nameIndex = name.indexOf("20x")
//if(nameIndex != -1)
//{
//    tissueName = "20x " + tissueName
//}
//    
//nameIndex = name.indexOf("high")
//if(nameIndex != -1)
//{
//    tissueName = "High Exp " + tissueName
//}

foldername = buildFilePath(PROJECT_BASE_DIR, 'detection results')
filename = buildFilePath(foldername, 'results.csv')
mkdirs(foldername)
pathImage = buildFilePath(foldername, tissueName+' cell expansion '+i+'.tif')
writeRenderedImage(viewer, pathImage)
/*
saveDetectionMeasurements(pathDetectionsFile)

*/
File resultsFile = new File(filename)
if(!resultsFile.exists())
{
    resultsFile.append("File Name,RFP Cell Count,DAPI Cell Count,Sigma \n")
}
resultsFile.append(tissueName+","+rfpCells+","+detections.size()+","+i+"\n")
}