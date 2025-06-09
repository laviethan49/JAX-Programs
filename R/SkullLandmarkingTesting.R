library(rgl)
library(geomorph)
library(openxlsx)

landmarksXLSX <- read.xlsx('C:\\Users\\Public\\Documents\\Git\\Jax-Programs\\R\\SkullLandmarkingFiles\\Skull Measurements Naa10_Naa12_Skull_93 - Results.xlsx', colNames = FALSE)
landmarksCSV <- read.csv('C:\\Users\\Public\\Documents\\Git\\Jax-Programs\\R\\SkullLandmarkingFiles\\Naa10_Naa12_Skull_93.csv', skip = 1)
landmarksCSV <- landmarksCSV[, -1]
landmarksCSV <- landmarksCSV[, 1:(ncol(landmarksCSV) - 3)]

coords <- as.matrix(landmarksCSV[, c("X", "Y", "Z")])
coords_array <- arrayspecs(coords, p = nrow(coords), k = 3)

plot3d(coords_array[, , 1], type = "s", col = "blue", size = 1, xlab = "X", ylab = "Y", zlab = "Z")
