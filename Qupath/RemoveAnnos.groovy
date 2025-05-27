//def oldAnnotations = getAnnotationObjects()
//def realAnnotations = []
//oldAnnotations.each {value->
//    if(value.getName() != null) {
//        if(value.getName().toLowerCase() != "right" && value.getName().toLowerCase() != "left") {
//            realAnnotations.add(value)
//        }
//    }
//}
//oldAnnotations = realAnnotations
//oldAnnotations.each {value->
//    removeObject(value, false)
//}
clearAnnotations()