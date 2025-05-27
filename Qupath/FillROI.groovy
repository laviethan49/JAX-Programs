import qupath.lib.roi.RoiTools
import qupath.lib.geom.Point2

anno = getSelectedObject()
annoROI = anno.getROI()
centroidX = annoROI.getCentroidX()*0.65
centroidY = annoROI.getCentroidY()*0.65

roiPoints = annoROI.getAllPoints().sort()

lengthAngles = [:]

roiPoints.each {point->
    xCoord = point.getX()*0.65
    yCoord = point.getY()*0.65
    xLength = xCoord - centroidX
    yLength = yCoord - centroidY
    cLength = Math.sqrt(Math.pow(xLength, 2) + Math.pow(yLength, 2))
//    println("X Length: "+xLength+" Y Length: "+yLength+" C Length: "+cLength)

    angle = Math.toDegrees(angle(cLength, yLength, xLength))
//    println(angle)
    
    if(xLength < 0 && yLength < 0) {
        //Third quadrant
        angle = angle + 90
    } else if(yLength < 0) {
        //Fourth quadrant
        angle = angle + 180
    }
    double angleCheck = Math.floor(angle*10)/10
//    println("Angle: "+angleCheck+" Distance: "+cLength)
    
    if(!lengthAngles.containsKey(angleCheck)) {
        lengthAngles.put(angleCheck, point)
    } else if(lengthAngles.containsKey(angleCheck)) {
        storedPoint = lengthAngles[angleCheck]
        storedX = storedPoint.getX()*0.65
        storedY = storedPoint.getY()*0.65
        storedXLength = storedX - centroidX
        storedYLength = storedY - centroidY
        storedDistance = Math.sqrt(Math.pow(storedXLength, 2) + Math.pow(storedYLength, 2))
        if(storedDistance < cLength) {
            lengthAngles[angleCheck] = new Point2(xCoord/0.65, yCoord/0.65)
//            println("Old: "+storedDistance+" New: "+cLength+" Angle: "+angleCheck)
        }
    }
}

pointList = []

lengthAngles.each {key, value->
    pointList.add(lengthAngles[key])
}

println(lengthAngles)

plane = getCurrentViewer().getImagePlane()
bindingROI = ROIs.createPolygonROI(pointList, plane)
newAnno = PathObjects.createAnnotationObject(bindingROI)
addObject(newAnno)

public static double angle(double a, double b, double c) {
    return Math.acos((Math.pow(a, 2) + Math.pow(b, 2) - Math.pow(c, 2)) / (2 * a * b));
}