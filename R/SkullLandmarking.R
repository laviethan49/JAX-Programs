library(rgl)
library(geomorph)
library(randomcoloR)

files <- list.files(path = "C:/Users/Public/Documents/Git/Jax-Programs/R/SkullLandmarkingFiles/", pattern="\\.csv$", full.names=TRUE, recursive=TRUE)

# Read and process each file
landmark_list <- list()
label_list <- list()

for (file in files) {
  data <- read.csv(file, skip = 1)
  labels <- data[, c("Name")] # Save first column (landmark labels)
  landmark_list[[length(landmark_list) + 1]] <- as.matrix(data[, c("X", "Y", "Z")])
  label_list[[length(label_list) + 1]] <- labels
}

p <- nrow(landmark_list[[1]])
k <- 3
n <- length(landmark_list)
coords_array <- array(unlist(landmark_list), dim = c(p, k, n))

# Normalize using GPA
gpa_result <- gpagen(coords_array)

# Plot the first specimen with labels
open3d()
colors <- distinctColorPalette(p) # Assign a unique color to each specimen

for (i in 1:n) {
  coords <- gpa_result$coords[, , i]
  for (j in 1:p) {
    spheres3d(coords[j, 1], coords[j, 2], coords[j, 3], radius = 0.0025, color = colors[j])
  }
}

axis3d("x", tick = FALSE, labels=FALSE)
axis3d("y", tick = FALSE, labels=FALSE)
axis3d("z", tick = FALSE, labels=FALSE)

numbered_labels <- paste0(seq_along(label_list[[1]]), ": ", label_list[[1]])
text3d(gpa_result$coords[, , 1], texts = numbered_labels, cex = 1, adj = c(0.5, -3))
legend3d("topright", legend = numbered_labels, col = colors, pch = 16, cex = 1.25, inset = c(0.02))