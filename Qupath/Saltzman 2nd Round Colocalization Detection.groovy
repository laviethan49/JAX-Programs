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
fileName = buildFilePath(folderName, 'Saltzman_'+imageName+'.csv')
roiFileName = buildFilePath(folderName, 'Saltzman_'+imageName+'.geojson')

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold, DAPI Nuclei Count, tdTomato+ Cell Count, tdTomato+ Percentage\n")
    }
    resultsFile.append(imageName)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append("\n")
}

clearDetections()
deselectAll()
removedDetections = []
overlappedROIs = []
overlapthreshold = 0.5

annotations = getAnnotationObjects()
annotations.each {anno->
    //Select current annotation
    getCurrentHierarchy().getSelectionModel().setSelectedObject(anno, true);
    
    //Run cell detection for DAPI channel, creating detections for nuclei
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 20,  "medianRadiusMicrons": 0,  "sigmaMicrons": 1.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 3,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
    //Create detections for RFP channel where threshold of signal is above the pixel classifier, 2000
    createDetectionsFromPixelClassifier("tdTomato +", 0.0, 0.0, "SPLIT", "SELECT_NEW");
    //Creates measurements for selected detections, as they are not generated when the detections are.
    addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "MAX_DIAMETER", "MIN_DIAMETER") 

    //Gets all detections within current annotation
    detections = anno.getChildObjects()
    //From all detections in the current annotation, get the detections that are the RFP areas
    classifiedDetections = detections.findAll {it.getClassifications().contains("RFP")}
    //From all detections in the current annotation, get the detections that are the nuclei
    nuclei = detections.findAll {it.getClassifications().size() == 0}

    //Add all RFP detections that don't resemble a cell (a circularity less than 0.4) to an array to remove them below
//    classifiedDetections.each {det->
//        circularity = det.getMeasurementList()
//
//        if(circularity["Circularity"] < 0.4)
//        {
//            getCurrentHierarchy().getSelectionModel().setSelectedObject(det, true)
//            obj = getSelectedObject()
//            removedDetections.add(obj)
//        }
//    }
//    //Removes the non cell like RFP detections
//    if(removedDetections.size() > 0)
//    {
//        removeObjects(removedDetections, false)
//    }
    
    //Need to add step to see if there is already an RFP detection matched to the nuclei detection
    
    //Cycle through each of the nuclei detections. If it overlaps the most with an RFP detection(set by overlapthreshold above), count it as a postive cell.
    //If the ratio is not high enough, count it anyway, but only one from that RFP detection, not all lower ratios also.
    totalNuclei = nuclei.size()//Total nuclei for finding % done during process
    positiveRFPObjects = [:]//Arraylist for positive "cells" detected
    colocalizedDAPINuclei = []
    rfpOverlapObjects = []//For the case where multiple overlaps that fit the criteria for one RFP detection object
    
    nuclei.eachWithIndex {nuc, key->
        println("Done: "+key+"/"+totalNuclei) 
        nucROI = nuc.getROI()//Get bounds of nucleus, DAPI
        classifiedDetections.each {rfpDet->
            rfpROI = rfpDet.getROI()//Get bounds of RFP detected object, from pixel classifier
            overlapROI = RoiTools.combineROIs(nucROI, rfpROI, CombineOp.INTERSECT)//Bounds of area where RFP object overlaps nucleus object
            scaledArea = overlapROI.getArea()*0.65*0.65
            rfpOverlapObject = PathObjects.createDetectionObject(overlapROI, getPathClass("RFP in Nuclei"))//Making that object detected above into a "real" object, AKA detection
            if(!overlapROI.isEmpty() && !colocalizedDAPINuclei.contains(nuc.getID()) && scaledArea >= 1) {//If overlap is not 0
                ratio = (overlapROI.getArea()/nucROI.getArea())//How much does it overlap
                if(ratio >= overlapthreshold) {//If above the threshold, add the object to the array, and update the entry to include the new object also.
                    rfpOverlapObjects.add(rfpOverlapObject)
                    positiveRFPObjects[rfpDet.getID()] = ["ratio":ratio, "rfpOverlapObject":rfpOverlapObjects, "rfpDet":rfpDet, "nucleus":nuc]
                    colocalizedDAPINuclei.add(nuc.getID())
                }
                else {//If below the ratio and there is no entry yet for that RFP object already, add it to the array
                    if(positiveRFPObjects[rfpDet.getID()] == null) {
                        positiveRFPObjects[rfpDet.getID()] = ["ratio":ratio, "rfpOverlapObject":rfpOverlapObject, "rfpDet":rfpDet, "nucleus":nuc]
                        colocalizedDAPINuclei.add(nuc.getID())
                    }
                    else {//If there is an entry, take only one and update the current one to be the one with the larger ratio
                        newRatio = ratio
                        previousRatio = positiveRFPObjects[rfpDet.getID()]["ratio"]
                        if(newRatio > previousRatio) {
                            positiveRFPObjects[rfpDet.getID()] = ["ratio":ratio, "rfpOverlapObject":rfpOverlapObject, "rfpDet":rfpDet, "nucleus":nuc]
                            colocalizedDAPINuclei.add(nuc.getID())
                        }
                    }
                    
                }
            }
        }
    }    
    //For each of the objects added to the array above, make them show up as detections and make them child objects of the RFP detection. Selecting them after they are made, create measurements, and deselect.
    positiveCellCount = 0
    positiveRFPObjects.eachWithIndex {obj, key->
        currentObj = positiveRFPObjects[positiveRFPObjects.keySet()[key]]//For some reason, the obj variable isn't the same thing as doing this, which is what's needed to access the values of the array list.
        currentObj["rfpOverlapObject"].each {
            currentObj["rfpDet"].addChildObject(it)
            it.setPathClass(getPathClass("colocalized"))
            getCurrentHierarchy().getSelectionModel().setSelectedObject(it, true)//All this just to select the detection created on the line above
            addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "MAX_DIAMETER", "MIN_DIAMETER")
            deselectAll()
            positiveCellCount++
        }
    }
    getPathClass("colocalized").setColor(getColorRGB(0,150,150))
    fireHierarchyUpdate()
    
//    resultsFile.append("Image Name, Background Threshold, DAPI Nuclei Count, tdTomato+ Cell Count, tdTomato+ Percentage\n")
    positivePercentage = (positiveCellCount/nuclei.size())*100
    makeFile(["2000", nuclei.size(), positiveCellCount, positivePercentage])
    //Deselect everything so the next loop will only focus on the next annotation
    deselectAll()
}

exportAllObjectsToGeoJson(roiFileName, "FEATURE_COLLECTION")

println "Done"