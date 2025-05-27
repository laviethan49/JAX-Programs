// Size in pixels at the base resolution
// note that the actual size will be one pixel larger in each dimension

imageData = getCurrentImageData()
server = imageData.getServer()
pixelWidth = server.getMetadata()['pixelWidthMicrons']
pixelHeight = server.getMetadata()['pixelHeightMicrons']
pixelCal = (pixelWidth+pixelHeight)/2

int size = 1000/pixelCal

// Get center pixel
def viewer = getCurrentViewer()
int cx = server.getWidth()/2
int cy = server.getHeight()/2

// Create & add annotation

def roi = ROIs.createRectangleROI(cx-size/2, cy-size/2, size, size, getCurrentViewer().getImagePlane())

rgb = getColorRGB(255, 1, 1)
pathClass = getPathClass('New Class', rgb)

//Use the line below to change the color after running the script once. You cannot "construct over" an already created class to change the color.
//pathClass.setColor(rgb)

def annotation = PathObjects.createAnnotationObject(roi, pathClass)
addObject(annotation)
fireHierarchyUpdate()