import qupath.lib.roi.ShapeSimplifier
import qupath.lib.roi.ROIs
import qupath.lib.roi.RoiTools

plane = getCurrentViewer().getImagePlane()

anno = getSelectedObject()
annoROI = anno.getROI()
roiPoints = annoROI.getAllPoints()

println(roiPoints.size())

simplifiedROI = RoiTools.fillHoles(annoROI)
simplifiedROI = ShapeSimplifier.simplifyShape(annoROI, 5.00)
simplifiedPoints = simplifiedROI.getAllPoints()

simplifiedAnno = PathObjects.createAnnotationObject(simplifiedROI)
addObject(simplifiedAnno)

println(simplifiedPoints.size())

listCircles = []
scaled = 100/0.65

simplifiedPoints.each {point->
    xPoint = point.getX()
    yPoint = point.getY()
    circleRoi = ROIs.createEllipseROI(xPoint-(scaled/2), yPoint-(scaled/2), scaled, scaled, plane)
    listCircles.add(circleRoi)
}

combinedRoi = RoiTools.union(listCircles)
combinedRoi = RoiTools.fillHoles(combinedRoi)
combinedAnno = PathObjects.createAnnotationObject(combinedRoi)
addObject(combinedAnno)

deselectAll()
getCurrentHierarchy().getSelectionModel().setSelectedObject(combinedAnno, true)
combinedAnno.setName("spreadROI")

runPlugin('qupath.lib.plugins.objects.DilateAnnotationPlugin', '{"radiusMicrons":-50.0,"lineCap":"ROUND","removeInterior":false,"constrainToParent":false}')

removeObject(simplifiedAnno, true)
removeObject(combinedAnno, true)

annotations = getAnnotationObjects()
annotations.each {anno->
//    println(anno.getName())
    if(anno.getName() == "spreadROI") {
        spreadROI = anno
    }
}

annoROI = spreadROI.getROI()
newROIs = RoiTools.splitROI(annoROI)
biggestArea = 0.0
newROIs.each {roi->
    roiArea = roi.getArea()
    if(biggestArea < roiArea) {
        currentROI = roi
        biggestArea = roiArea
    }
}