/*
 * Copyright (c) 2021.  Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 *
 *
 */

package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.DistanceMeasure;
import org.esa.snap.core.util.math.EuclideanDistance;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.ConstantDescriptor;
import java.awt.Rectangle;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * @author Marco Peters
 */
public class MisrAlgo {
    private final Product slstrSourceProduct;
    private final HashMap<String, Map<int[], int[]>> misrMap;
    private final boolean fillEmptyPixels;
    private final boolean orphan;
    private final PlanarImage olciValidMaskImage;

    public MisrAlgo(Product olciSourceProduct, Product slstrSourceProduct, HashMap<String, Map<int[], int[]>> misrMap, boolean fillEmptyPixels, boolean orphan) {
        this.slstrSourceProduct = slstrSourceProduct;
        this.misrMap = misrMap;
        this.fillEmptyPixels = fillEmptyPixels;
        this.orphan = orphan;
        RasterDataNode oa17_radiance = olciSourceProduct.getRasterDataNode("Oa17_radiance");
        if (oa17_radiance.isValidMaskUsed()) {
            olciValidMaskImage = oa17_radiance.getValidMaskImage();
        } else {
            olciValidMaskImage = ConstantDescriptor.create((float) oa17_radiance.getRasterWidth(), (float) oa17_radiance.getRasterHeight(), new Byte[]{1}, null);
        }

    }


    void doMisregistration(Band targetBand, Tile targetTile, Rectangle targetRectangle) {

        final String targetBandName = targetBand.getName();
        final double targetNoDataValue = targetBand.getNoDataValue();
        if (slstrSourceProduct.containsBand(targetBandName)) {
            Band sourceBand = slstrSourceProduct.getBand(targetBandName);
            int sourceRasterWidth = sourceBand.getRasterWidth();
            int sourceRasterHeight = sourceBand.getRasterHeight();
            Map<int[], int[]> map = getPixelMap(targetBandName);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    targetTile.setSample(x, y, targetNoDataValue);
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition != null) {
                        final int slstrGridPosX = slstrGridPosition[0];
                        final int slstrGridPosY = slstrGridPosition[1];
                        if (slstrGridPosX < sourceRasterWidth && slstrGridPosY < sourceRasterHeight) {
                            double reflecValue = sourceBand.getSampleFloat(slstrGridPosX, slstrGridPosY);
                            if (reflecValue < 0) {
                                reflecValue = targetNoDataValue;
                            }
                            targetTile.setSample(x, y, reflecValue);
                        }
                    }
                }
            }
            //Orphan pixels
            if (orphan) {
                String parentPath = slstrSourceProduct.getFileLocation().getParent();
                String netcdfDataPath = parentPath + "/" + targetBandName + ".nc";
                if (Files.exists(Paths.get(netcdfDataPath))) {
                    try (NetcdfFile netcdf = NetcdfFiles.open(netcdfDataPath)){
                        Variable orphanVariable = netcdf.findVariable(targetBandName.replace("radiance_", "radiance_orphan_"));
                        if (orphanVariable == null) {
                            throw new OperatorException(String.format("No information about orphans found in file '%s'", netcdfDataPath));
                        }
                        final Attribute scale_factorAttribute = orphanVariable.findAttribute("scale_factor");
                        double scaleFactor = 1.0;
                        if (scale_factorAttribute != null) {
                            final Number scale_factor = scale_factorAttribute.getNumericValue();
                            if (scale_factor != null) {
                                scaleFactor = (double) scale_factor;
                            }
                        }


                        final Array orphanData = orphanVariable.read();
                        final int[] dataShape = orphanData.getShape(); // shape is [2400, 374] for S3_radiance_orphan_an
                        final Index rawIndex = orphanData.getIndex();
                        Map<int[], int[]> mapOrphan = getOrphanMap(targetBandName);
                        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                                int[] position = {x, y};
                                int[] slstrOrphanPosition = mapOrphan.get(position);
                                if (slstrOrphanPosition != null) {
                                    final int orphanPosX = slstrOrphanPosition[0];
                                    final int orphanPosY = slstrOrphanPosition[1];
                                    if (orphanPosX < dataShape[0] && orphanPosY < dataShape[1]) {
                                        rawIndex.set(orphanPosY, orphanPosX); // Dimension is Y, X  --> so 'wrong' order of Y and X here
                                        double dataValue = orphanData.getDouble(rawIndex);
                                        if (dataValue > 0) {
                                            targetTile.setSample(x, y, dataValue * scaleFactor);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ioe) {
                        SystemUtils.LOG.log(Level.WARNING, String.format("Could not process file %s: %s", netcdfDataPath, ioe.getMessage()));
                    }
                } else {
                    SystemUtils.LOG.log(Level.FINE, String.format("File %s does not exist", netcdfDataPath));
                }
            }

            if (fillEmptyPixels) {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        if (targetTile.getSampleDouble(x, y) == targetNoDataValue) {
                            double neighborPixel = getNeighborPixel(x, y, targetBand, map, sourceBand);
                            targetTile.setSample(x, y, neighborPixel);
                        }
                    }
                }
            }

        } else if (targetBandName.equals("misr_flags")) {
            Map<int[], int[]> map = getPixelMap("S3_radiance_an");
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition != null) {
                        targetTile.setSample(x, y, 1);
                    } else {
                        targetTile.setSample(x, y, 0);
                    }
                }
            }
        } else if (targetBandName.equals("filled_flags")) {
            Map<int[], int[]> map = getPixelMap("S3_radiance_an");
            final Raster validMaskData = olciValidMaskImage.getData(targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition == null && validMaskData.getSample(x, y, 0) != 0) {
                        targetTile.setSample(x, y, 1);
                    }
                }
            }
        }
    }

    private double getNeighborPixel(int x, int y, Band targetBand, Map<int[], int[]> map, Band sourceBand) {
        double neighborPixel = targetBand.getNoDataValue();
        int[] position = {x, y};
        int[] slstrGridPosition = map.get(position);
        GeoPos pixelGeoPos = targetBand.getGeoCoding().getGeoPos(new PixelPos(x, y), null);
        if (slstrGridPosition != null) {
            return sourceBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]);
        } else {
            EuclideanDistance euclideanDistance = new EuclideanDistance(pixelGeoPos.getLon(), pixelGeoPos.getLat());
            for (int size = 3; size < 10; size += 2) {
                neighborPixel = searchClosetPixel(size, sourceBand, euclideanDistance, x, y, targetBand, map);
                if (neighborPixel != targetBand.getNoDataValue()) {
                    return neighborPixel;
                }
            }
        }
        return neighborPixel;
    }

    private double searchClosetPixel(int size, Band sourceBand, DistanceMeasure distanceMeasure, int x, int y, Band targetBand, Map<int[], int[]> map) {
        double distance = Double.MAX_VALUE;
        double neighborPixel = targetBand.getNoDataValue();
        int step = size / 2;
        final GeoCoding sourceGeoCoding = sourceBand.getGeoCoding();
        for (int i = 0; i < size; i += 1) {
            for (int j = 0; j < size; j += 1) {
                int[] neighborPos = {x - step + i, y - step + j};
                int[] slstrNeighbor = map.get(neighborPos);
                if (slstrNeighbor != null) {
                    GeoPos neighborGeoPos = sourceGeoCoding.getGeoPos(new PixelPos(slstrNeighbor[0], slstrNeighbor[1]), null);
                    double neighborDist = distanceMeasure.distance(neighborGeoPos.getLon(), neighborGeoPos.getLat());
                    if (neighborDist < distance) {
                        neighborPixel = sourceBand.getSampleFloat(slstrNeighbor[0], slstrNeighbor[1]);
                        distance = neighborDist;
                    }
                }
            }
        }
        return neighborPixel;
    }

    private Map<int[], int[]> getPixelMap(String bandName) {
        if (bandName.contains("_ao")) {
            return misrMap.get("aoPixelMap");
        } else if (bandName.contains("_bo")) {
            return misrMap.get("boPixelMap");
        } else if (bandName.contains("_co")) {
            return misrMap.get("coPixelMap");
        } else if ((bandName.contains("S1") && bandName.contains("_an")) || (bandName.contains("S1") && bandName.contains("_bn")) || (bandName.contains("S1") && bandName.contains("_cn"))) {
            return misrMap.get("S1PixelMap");
        } else if ((bandName.contains("S2") && bandName.contains("_an")) || (bandName.contains("S2") && bandName.contains("_bn")) || (bandName.contains("S2") && bandName.contains("_cn"))) {
            return misrMap.get("S2PixelMap");
        } else if ((bandName.contains("S3") && bandName.contains("_an")) || (bandName.contains("S3") && bandName.contains("_bn")) || (bandName.contains("S3") && bandName.contains("_cn"))) {
            return misrMap.get("S3PixelMap");
        } else if ((bandName.contains("S4") && bandName.contains("_an")) || (bandName.contains("S4") && bandName.contains("_bn")) || (bandName.contains("S4") && bandName.contains("_cn"))) {
            return misrMap.get("S4PixelMap");
        } else if ((bandName.contains("S5") && bandName.contains("_an")) || (bandName.contains("S5") && bandName.contains("_bn")) || (bandName.contains("S5") && bandName.contains("_cn"))) {
            return misrMap.get("S5PixelMap");
        } else if ((bandName.contains("S6") && bandName.contains("_an")) || (bandName.contains("S6") && bandName.contains("_bn")) || (bandName.contains("S6") && bandName.contains("_cn"))) {
            return misrMap.get("S6PixelMap");
        } else if ((bandName.contains("_an") || bandName.contains("_bn") || bandName.contains("_cn"))) {// todo - does not exist, right? Because it is covered by the above
            return misrMap.get("S3PixelMap");
        } else {
            throw new IllegalArgumentException("No pixel mapping known for band " + bandName);
        }
    }

    private Map<int[], int[]> getOrphanMap(String bandName) {
        if (bandName.contains("_ao")) {
            return misrMap.get("aoOrphanMap");
        } else if (bandName.contains("_bo")) {
            return misrMap.get("boOrphanMap");
        } else if (bandName.contains("_co")) {
            return misrMap.get("coOrphanMap");
        } else if ((bandName.contains("S1") && bandName.contains("_an")) || (bandName.contains("S1") && bandName.contains("_bn")) || (bandName.contains("S1") && bandName.contains("_cn"))) {
            return misrMap.get("S1OrphanMap");
        } else if ((bandName.contains("S2") && bandName.contains("_an")) || (bandName.contains("S2") && bandName.contains("_bn")) || (bandName.contains("S2") && bandName.contains("_cn"))) {
            return misrMap.get("S2OrphanMap");
        } else if ((bandName.contains("S3") && bandName.contains("_an")) || (bandName.contains("S3") && bandName.contains("_bn")) || (bandName.contains("S3") && bandName.contains("_cn"))) {
            return misrMap.get("S3OrphanMap");
        } else if ((bandName.contains("S4") && bandName.contains("_an")) || (bandName.contains("S4") && bandName.contains("_bn")) || (bandName.contains("S4") && bandName.contains("_cn"))) {
            return misrMap.get("S4OrphanMap");
        } else if ((bandName.contains("S5") && bandName.contains("_an")) || (bandName.contains("S5") && bandName.contains("_bn")) || (bandName.contains("S5") && bandName.contains("_cn"))) {
            return misrMap.get("S5OrphanMap");
        } else if ((bandName.contains("S6") && bandName.contains("_an")) || (bandName.contains("S6") && bandName.contains("_bn")) || (bandName.contains("S6") && bandName.contains("_cn"))) {
            return misrMap.get("S6OrphanMap");
        } else if ((bandName.contains("_an") || bandName.contains("_bn") || bandName.contains("_cn"))) {// todo - does not exist, right? Because it is covered by the above
            return misrMap.get("S3OrphanMap");
        } else {
            throw new IllegalArgumentException("No orphan mapping known for band " + bandName);
        }
    }
}


