# Assume landmarks is a numeric 3D array: (num_points x 3 x num_specimens).
# Select a reference specimen (e.g., specimen 1).
# Install and load required packages:
# install.packages("pracma")  # for cross product and robust norm calculation
library(geomorph)
library(rgl)
library(pracma)
library(Rvcg)


# Assume that "landmarks" is your 3D array with dimensions:
#    num_points x 3 x num_specimens

# Choose the reference specimen that will define the orientation/plane.
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
landmarks <- array(unlist(landmark_list), dim = c(p, k, n))

gpa <- gpagen(landmarks)
plotAllSpecimens(gpa$coords)

specimen_index = length(files) - 5

stl_mesh <- vcgImport("C:/Users/Public/Documents/Git/Jax-Programs/R/Skull Landmarking Results/113_skull.stl")
simplified_mesh <- vcgQEdecim(stl_mesh, percent = 0.05)
scaled_mesh <- simplified_mesh

# Retrieve the specimen's original landmarks and corresponding GPA-aligned landmarks.
orig_lm    <- landmarks[,,specimen_index]    # Original specimen landmarks
aligned_lm <- gpa$coords[,,specimen_index]     # GPA-aligned landmarks

# Compute the centroids (means)
orig_centroid    <- colMeans(orig_lm)
aligned_centroid <- colMeans(aligned_lm)

# Center the original specimen landmarks
A <- sweep(orig_lm, 2, orig_centroid, FUN = "-")
B <- sweep(aligned_lm, 2, aligned_centroid, FUN = "-")

# Compute centroid sizes (using all points)
cs_orig    <- sqrt(sum(A^2))
cs_aligned <- sqrt(sum(B^2))
scale_factor <- cs_aligned / cs_orig

# Compute the best-fit rotation via SVD:
svd_out <- svd(t(A) %*% B)
R <- svd_out$v %*% t(svd_out$u)
if (det(R) < 0) {
  svd_out$v[,3] <- -svd_out$v[,3]
  R <- svd_out$v %*% t(svd_out$u)
}

# Extract STL vertices. In an rgl mesh3d object, vertices are in the vb slot as a 4 x n matrix.
stl_coords <- t(scaled_mesh$vb[1:3, ])  # n_vertices x 3 matrix
transformed_mesh <- t( R %*% t( (stl_coords - matrix(orig_centroid, nrow(stl_coords), 3, byrow = TRUE)) ) ) + matrix(aligned_centroid, nrow(stl_coords), 3, byrow = TRUE)

new_mesh <- tmesh3d(
  vertices = t(transformed_mesh),   # Must be 3 x n
  indices  = scaled_mesh$it,         # Adjust this slot name if your object uses "ib" or similar
  homogeneous = TRUE
)

open3d()  # Open a new 3D window
colors <- distinctColorPalette(p) # Assign a unique color to each specimen

for (i in 1:n) {
  coords <- gpa$coords[, , i] * gpa$Csize[i]
  for (j in 1:p) {
    spheres3d(coords[j, 1], coords[j, 2], coords[j, 3], radius = 5, color = colors[j])
  }
}

# Overlay the transformed, decimated STL mesh in semitransparent gray.
shade3d(new_mesh, color = "gray", alpha = 0.5)

#rgl.pop()
#rm(new_mesh)