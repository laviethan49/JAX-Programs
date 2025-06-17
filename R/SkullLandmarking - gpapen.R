library(rgl)
library(geomorph)
library(randomcoloR)
library(Rvcg)

files <- list.files(path = "C:/Users/Public/Documents/Git/Jax-Programs/R/Skull Landmarking Results/", pattern="\\.csv$", full.names=TRUE, recursive=TRUE)

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
  coords <- coords_array[, , i]
  for (j in 1:p) {
    spheres3d(coords[j, 1], coords[j, 2], coords[j, 3], radius = 5, color = colors[j])
  }
}

specimen_index <- length(files)-5  # adjust to your specimen of interest
#Points for specific specimen only
coords <- coords_array[, , specimen_index]
for (j in 1:p) {
  spheres3d(coords[j, 1], coords[j, 2], coords[j, 3], radius = 5, color = colors[j])
}

axis3d("x")#, tick = FALSE, labels=FALSE)
axis3d("y")#, tick = FALSE, labels=FALSE)
axis3d("z")#, tick = FALSE, labels=FALSE)

numbered_labels <- paste0(seq_along(label_list[[1]]), ": ", label_list[[1]])
text3d(gpa_result$coords[, , 1], texts = numbered_labels, cex = 1, adj = c(0.5, -3))
legend3d("topright", legend = numbered_labels, col = colors, pch = 16, cex = 1.25, inset = c(0.02))

orig_lm    <- coords_array[,,specimen_index]
aligned_lm <- gpa_result$coords[,,specimen_index]

# Compute centroids of the original and aligned landmarks
orig_centroid    <- colMeans(orig_lm)
aligned_centroid <- colMeans(aligned_lm)

# Center the landmarks about zero
A <- scale(orig_lm, center = TRUE, scale = FALSE)
B <- scale(aligned_lm, center = TRUE, scale = FALSE)

# Compute the best-fit rotation matrix: using singular value decomposition
svd_out <- svd(t(A) %*% B)
R <- svd_out$v %*% t(svd_out$u)

stl_mesh <- vcgImport("C:/Users/Public/Documents/Git/Jax-Programs/R/Skull Landmarking Results/113_skull.stl")
simplified_mesh <- vcgQEdecim(stl_mesh, percent = 0.05)

# Extract the vertices (rgl's mesh3d object stores coordinates in a 4 x n matrix in the vb slot)
stl_coords <- t(simplified_mesh$vb[1:3, ])

# Compute the centroid size of the original landmark configuration
orig_centroid <- colMeans(orig_lm)
centered_orig <- orig_lm - matrix(orig_centroid, nrow(orig_lm), 3, byrow = TRUE)
centroid_size_orig <- sqrt(sum(centered_orig^2))

# Compute the centroid size of the aligned landmarks from gpagen (if available)
aligned_centroid <- colMeans(aligned_lm)
centered_aligned <- aligned_lm - matrix(aligned_centroid, nrow(aligned_lm), 3, byrow = TRUE)
centroid_size_aligned <- sqrt(sum(centered_aligned^2))

# Calculate the scaling factor
scale_factor <- (centroid_size_aligned / centroid_size_orig)

# You might need to apply the same factor to the mesh vertices

# Compute the mesh centroid
mesh_centroid <- colMeans(stl_coords)

# Center the mesh vertices
centered_coords <- sweep(stl_coords, 2, mesh_centroid)

# Apply scaling
scaled_coords <- centered_coords * scale_factor

# Rotate the scaled coordinates using the rotation matrix computed earlier (R)
rotated_coords <- scaled_coords %*% R

# Position the mesh correctly by translating it:
transformed_coords <- rotated_coords + matrix(aligned_centroid, nrow(rotated_coords), 3, byrow = TRUE)

# Rebuild the vb matrix with homogeneous coordinates:
scaled_mesh <- simplified_mesh
scaled_mesh$vb[1:3, ] <- t(transformed_coords)

# Visualize the transformed mesh alongside your scatterplot
open3d()
shade3d(simplified_mesh, color = "gray", alpha = 1)

rgl.pop()
rm(simplified_mesh)

# Calculate bounding box extents for the mesh vertices:
mesh_min <- apply(stl_coords, 2, min)
mesh_max <- apply(stl_coords, 2, max)
mesh_extent <- mesh_max - mesh_min

# Calculate bounding box extents for the corresponding landmark set:
lm_min <- apply(orig_lm, 2, min)
lm_max <- apply(orig_lm, 2, max)
lm_extent <- lm_max - lm_min

cat("Mesh extents:", mesh_extent, "\n")
cat("Landmark extents:", lm_extent, "\n")
