#Branch Name: mp_use_raw_data
This branch uses the raw data buffer. This should improve processing time and should reduce the amount 
of data of the resulting target.
For compressed NetCDF the file size was reduced from 2.5 GB to 1.8 GB, but processing time is still the same.
Also the data changed quite significantly. Histograms are different and have some arteifacts at the top if the image.
The would need further investigation.

