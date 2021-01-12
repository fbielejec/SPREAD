package com.spread.parsers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.spread.contouring.ContourMaker;
import com.spread.contouring.ContourPath;
import com.spread.contouring.ContourWithSnyder;
import com.spread.exceptions.SpreadException;
import com.spread.utils.ParsersUtils;
import com.spread.utils.PrintUtils;
import com.spread.utils.ProgressBar;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;

import jebl.evolution.io.ImportException;
import jebl.evolution.io.NexusImporter;
import jebl.evolution.trees.RootedTree;

import lombok.Setter;

public class TimeSlicerParser {

    @Setter
    private String treesFilePath;

    @Setter
    private String sliceHeightsFilePath;

    @Setter
    private int numberOfIntervals;

    @Setter
    private int burnIn;

    @Setter
    private String traitName;

    @Setter
    private String rrwRateName;

    @Setter
    private double hpdLevel;

    @Setter
    private int gridSize;

    public TimeSlicerParser() {
    }

    public TimeSlicerParser(String treesFilePath, // path to the trees file
                            int burnIn, //
                            String sliceHeightsFilePath,
                            String traitName, // 2D trait for contouring
                            String rrwRateName, // 2D trait rate attribute
                            double hpdLevel,
                            int gridSize
                            ) {
        this.treesFilePath = treesFilePath;
        this.sliceHeightsFilePath = sliceHeightsFilePath;
        this.burnIn = burnIn;
        this.traitName = traitName;
        this.rrwRateName = rrwRateName;
        this.hpdLevel = hpdLevel;
        this.gridSize = gridSize;
    }

    public TimeSlicerParser(String treesFilePath,
                            int burnIn,
                            int numberOfIntervals,
                            String traitName,
                            String rrwRateName,
                            double hpdLevel,
                            int gridSize
                            ) {
        this.treesFilePath = treesFilePath;
        this.burnIn = burnIn;
        this.numberOfIntervals = numberOfIntervals;
        this.traitName = traitName;
        this.rrwRateName = rrwRateName;
        this.hpdLevel = hpdLevel;
        this.gridSize = gridSize;
    }

    public String parse() throws Exception {

        // ---PARSE TREES---//

        int barLength = 100;
        int assumedTrees = getAssumedTrees(this.treesFilePath);
        double stepSize = (double) barLength / (double) assumedTrees;

        Double sliceHeights[] = null;
        if (this.sliceHeightsFilePath == null) {
            sliceHeights = generateSliceHeights(this.treesFilePath, this.numberOfIntervals);
        } else {
            SliceHeightsParser sliceHeightsParser = new SliceHeightsParser(this.sliceHeightsFilePath);
            sliceHeights = sliceHeightsParser.parseSliceHeights();
        }

        // sort them in ascending order
        Arrays.sort(sliceHeights);

        System.out.println("Using as slice heights: ");
        PrintUtils.printArray(sliceHeights);

        System.out.println("Reading trees (bar assumes " + assumedTrees + " trees)");

        ProgressBar progressBar = new ProgressBar(barLength);
        progressBar.start();

        System.out.println("0                        25                       50                       75                       100%");
        System.out.println("|------------------------|------------------------|------------------------|------------------------|");

        NexusImporter treesImporter = new NexusImporter(new FileReader(this.treesFilePath));
        ConcurrentHashMap<Double, List<double[]>> slicesMap = new ConcurrentHashMap<Double, List<double[]>>(sliceHeights.length);

        // Executor for threads
        int NTHREDS = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        Collection<Callable<Void>> tasks = new ArrayList<>();

        RootedTree currentTree;
        int treesRead = 0;
        int counter = 0;

        while (treesImporter.hasTree()) {

            currentTree = (RootedTree) treesImporter.importNextTree();
            if (counter >= burnIn) {

                // tasks.add(new TimeSliceTree(slicesMap, //
                //                             currentTree, //
                //                             sliceHeights, //
                //                             this.traitName, //
                //                             this.rrwRateName //
                //                             ));

                new TimeSliceTree(slicesMap, //
                                  currentTree, //
                                  sliceHeights, //
                                  this.traitName, //
                                  this.rrwRateName //
                                  ).call();

                treesRead++;
            } // END: burnin check

            counter++;
            double progress = (stepSize * counter) / barLength;
            progressBar.setProgressPercentage(progress);
        }

        // Wait until all threads are finished
        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException ex) {
            throw new SpreadException (ex.getMessage());
        }
        // executor.shutdown();
        // while (!executor.isTerminated()) {
        // }

        progressBar.showCompleted();
        progressBar.setShowProgress(false);

        System.out.print("\n");
        System.out.println("Analyzed " + treesRead + " trees with burn-in of " + burnIn + " for the total of " + counter + " trees");

        // --- MAKE CONTOURS ---//

        System.out.println("Creating contours for " + traitName + " trait at " + hpdLevel + " HPD level");
        System.out.println("0                        25                       50                       75                       100%");
        System.out.println("|------------------------|------------------------|------------------------|------------------------|");

        counter = 0;
        stepSize = (double) barLength / (double) slicesMap.size();

        progressBar = new ProgressBar(barLength);
        progressBar.start();

        // TODO : parallel execution
        for (Double sliceHeight : slicesMap.keySet()) {

            List<double[]> coords = slicesMap.get(sliceHeight);
            int n = coords.size();

            double[] x = new double[n];
            double[] y = new double[n];

            for (int i = 0; i < n; i++) {

                if (coords.get(i) == null) {
                    System.out.println("null found");
                }

                x[i] = coords.get(i)[ParsersUtils.LONGITUDE_INDEX];
                y[i] = coords.get(i)[ParsersUtils.LATITUDE_INDEX];

                // System.out.println(x [i] + " : " + y [i]);

            } // END: i loop

            // System.out.println("GS:" + gridSize);

            ContourMaker contourMaker = new ContourWithSnyder(x, y, gridSize);
            ContourPath[] paths = contourMaker.getContourPaths(hpdLevel);

            // for (ContourPath path : paths) {

            //     double[] longitude = path.getAllX();
            //     double[] latitude = path.getAllY();

            //     List<Coordinate> coordinateList = new ArrayList<Coordinate>();

            //     for (int i = 0; i < latitude.length; i++) {
            //         coordinateList.add(new Coordinate(latitude[i], longitude[i]));
            //     }

            //     Polygon polygon = new Polygon(coordinateList);

            //     String startTime = timeParser.getNodeDate(sliceHeight * timescaleMultiplier);

            //     HashMap<String, Object> areaAttributesMap = new HashMap<String, Object>();
            //     areaAttributesMap.put(Utils.HPD.toUpperCase(), hpdLevel);

            //     Area area = new Area(polygon, startTime, areaAttributesMap);
            //     areasList.add(area);

            // } // END: paths loop

            counter++;
            double progress = (stepSize * counter) / barLength;
            progressBar.setProgressPercentage(progress);

        } // END: iterate



        // TODO return JSON
        return "";
    }

    public static int getAssumedTrees(String file) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file));

        try {

            String mark = ";";
            int markCount = 0;
            int markBorder = 6;

            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;

            while ((readChars = is.read(c)) != -1) {

                empty = false;
                for (int i = 0; i < readChars; i++) {
                    if (String.valueOf((char) c[i]).equalsIgnoreCase(mark)) {
                        markCount++;
                    }

                    if (c[i] == '\n' && markCount > markBorder) {
                        count++;
                    }
                }
            }

            count = count - 1;
            return (count == 0 && !empty) ? 1 : count;
        } finally {
            is.close();
        }
    }

    private Double[] generateSliceHeights(String treesFilePath, int numberOfIntervals) throws IOException, ImportException {
        Double[] timeSlices = new Double[numberOfIntervals];

        NexusImporter treesImporter = new NexusImporter(new FileReader(this.treesFilePath));

        double maxRootHeight = 0;
        RootedTree currentTree = null;
        while (treesImporter.hasTree()) {
            currentTree = (RootedTree) treesImporter.importNextTree();

            double rootHeight = currentTree.getHeight(currentTree.getRootNode());

            if (rootHeight > maxRootHeight) {
                maxRootHeight = rootHeight;
            }

        }

        for (int i = 0; i < numberOfIntervals; i++) {
                timeSlices[i] = maxRootHeight - (maxRootHeight / (double) numberOfIntervals) * ((double) i);
        }

        return timeSlices;
    }

}
