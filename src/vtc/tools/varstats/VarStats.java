/************************************************
 * 	Summary Stats  
 * 
 * Filename
 * 	total variants:
 * 		SNVs:
 * 		InDels:	
 * 		Structural Variants:
 * 	Transition/transversion:
 * 	Mean/min/max for qual and depth
 * 	# of hetero.
 * 	# of Homo.
 * 	# of vars with miltiple alts -> variant context
 * 
 */

/************************************************
 *	Association Test
 *	Lets just get all the variants calculated to be able to perform
 *	an association test.
 */

package vtc.tools.varstats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.broadinstitute.variant.variantcontext.Allele;
import org.broadinstitute.variant.variantcontext.Genotype;
import org.broadinstitute.variant.variantcontext.VariantContext;

import vtc.datastructures.VariantPool;

public class VarStats {

    private static final String MaxInsertion = null;

    HashMap<String, String>     phenoInfo    = new HashMap<String, String>();

    // constructors

    public VarStats(TreeMap<String, VariantPool> allVPs, ArrayList<Object> phenoArgs, boolean printMulti, boolean sum, boolean assoc) {
        if (sum)
            IterateAndCount(allVPs, printMulti);
        if (assoc)
            doAssociation(allVPs, phenoArgs);
    }

    public VarStats() {
    }

    // Functions

    private void doAssociation(TreeMap<String, VariantPool> AllVPs, ArrayList<Object> phenoArgs) {

        if (phenoArgs != null) {
            // Make a structure to read in the phenotype information...
            phenoInfo = ParsePhenoFile(phenoArgs);
        }

        for (VariantPool VP : AllVPs.values()) {

            ArrayList<Association> association = new ArrayList<Association>();

            Object[] SampleList = VP.getSamples().toArray();
            ArrayList<String> CasePhenos = new ArrayList<String>();
            ArrayList<String> ControlPhenos = new ArrayList<String>();
            for (Object o : SampleList) {
                if (phenoInfo.containsKey(o)) {
                    String phenotype = phenoInfo.get(o);
                    // System.out.println(o + phenotype);
                    if (phenotype.equals("1")) {
                        CasePhenos.add((String) o);

                    } else if (phenotype.equals("2"))
                        ControlPhenos.add((String) o);
                    // System.out.println(o + "'s pheno is " +phenoInfo.get(o));
                }
            }

            Iterator<String> it = VP.getIterator();
            String currVarKey;
            int Num_SNPS = 0;
            String OutFile = "temp.txt";
            while (it.hasNext()) {

                currVarKey = it.next();
                VariantContext vc = VP.getVariant(currVarKey);
                OutFile = VP.getFile().getName();
                // Its a SNP now calculate frequencies
                Allele Ref = vc.getReference();
                List<Allele> Alts = vc.getAlternateAlleles();

                for (Allele A : Alts) {

                    Association Assoc = new Association(vc.getChr(), vc.getID(), vc.getStart(), vc.getReference().getBaseString(), A.getBaseString());
                    long[] CaseAlleleCount = Assoc.CaseControlCounts(CasePhenos, vc, A.getBaseString());
                    long[] ControlAlleleCount = Assoc.CaseControlCounts(ControlPhenos, vc, A.getBaseString());
                    Assoc.SetCaseRefCount(CaseAlleleCount[0]);
                    Assoc.SetCaseAltCount(CaseAlleleCount[1]);
                    Assoc.SetControlRefCount(ControlAlleleCount[0]);
                    Assoc.SetControlAltCount(ControlAlleleCount[1]);
                    ChiSquareTest test = new ChiSquareTest();
                    double PVal = test.chiSquareTestDataSetsComparison(ControlAlleleCount, CaseAlleleCount);

                    Assoc.SetPValue(PVal);

                    double OR = Assoc.calcOR(CaseAlleleCount, ControlAlleleCount);
                    Assoc.SetOR(OR);

                    association.add(Assoc);

                }

            }
            printToFile(association, OutFile);

        }

    }

    private HashMap<String, String> ParsePhenoFile(ArrayList<Object> phenofiles) {
        HashMap<String, String> phenos = new HashMap<String, String>();
        for (Object o : phenofiles) {
            // lets parse the phenotype file.
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader(o.toString()));
                String line;
                while ((line = br.readLine()) != null) {
                    // process the line.
                    String line1[] = line.split("\t");
                    phenos.put(line1[0], line1[1]);
                }
                br.close();
            } catch (FileNotFoundException e2) {
                // TODO Auto-generated catch block
                e2.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return phenos;
    }

    HashMap<String, String> MakePhenoList(Object[] SampleList, HashMap<String, String> phenoInfo) {
        HashMap<String, String> Phenos = new HashMap<String, String>();

        return Phenos;
    }

    private void printToFile(ArrayList<Association> A, String OutFile) {
        String outfile = OutFile.substring(0, OutFile.lastIndexOf("."));
        OutFile = outfile + ".txt";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(OutFile));
            out.write("Chr" + '\t' + "ID" + '\t' + "Pos" + '\t' + "Ref" + '\t' + "Alt" + '\t' + "CaseRefCount" + '\t' + "CaseAltCount" + '\t' + "ControlRefCount" + '\t' + "ControlAltCount" + '\t' + "OR" + '\t' + "P-Value" + '\n');
            for (Association a : A) {
                out.write(a.toString());
            }
            out.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /*
     * 
     * 
     * 
     * Association functions above, Summary functions below
     */

    private void IterateAndCount(TreeMap<String, VariantPool> allVPs, boolean printMulti) {

        ArrayList<String> files = new ArrayList<String>();
        NavigableSet<String> filenames = allVPs.descendingKeySet();
        Iterator<String> i = filenames.descendingIterator();
        while (i.hasNext()) {
            files.add(i.next());
        }

        int TotalNumVars = 0;
        int TotalQVars = 0;
        int TotalDPVars = 0;
        int TotalNumSNVs = 0;
        int TotalInDels = 0;
        int TotalInsertions = 0;
        int TotalDeletions = 0;
        int TotalStructVars = 0;
        int TotalNumHomoRef = 0;
        int TotalNumHomoVar = 0;
        int TotalNumHet = 0;
        int TotalNumMultiAlts = 0;
        double TotalQualScore = 0;
        double TotalMaxQScore = -1;
        double TotalMinQScore = Integer.MAX_VALUE;
        double TotalDepth = 0;
        int TotalMaxDepth = -1;
        int TotalMinDepth = Integer.MAX_VALUE;
        int TotalNumGeno = 0;
        double TotalTiCount = 0;
        double TotalTvCount = 0;
        double TotalTiTv = 0;
        double TotalGenoTiCount = 0;
        double TotalGenoTvCount = 0;
        double TotalGenoTiTv = 0;

        String QualError = "";
        String DepthError = "";

        ArrayList<String> Files = new ArrayList<String>();

        int count = 0;
        for (VariantPool VP : allVPs.values()) {

            int NumVars = 0;
            int QVars = 0;
            int DPVars = 0;
            int NumSNVs = 0;
            int InDels = 0;
            int Insertions = 0;
            int Deletions = 0;
            double minInsertion = 0;
            double maxInsertion = 0;
            double avgInsertion = 0;
            double minDeletion = 0;
            double maxDeletion = 0;
            double avgDeletion = 0;
            int StructVars = 0;
            int NumHomoRef = 0;
            int NumHomoVar = 0;
            int NumMultiAlts = 0;
            int NumHet = 0;
            double QualScore = 0;
            double MaxQScore = -1;
            double MinQScore = Integer.MAX_VALUE;
            double TiCount = 0;
            double TvCount = 0;
            double Depth = 0;
            int MaxDepth = -1;
            int MinDepth = Integer.MAX_VALUE;
            int NumGeno = 0;
            double TiTv = 0;
            double GenoTiCount = 0;
            double GenoTvCount = 0;
            double GenoTiTv = 0;
            String FileName = VP.getFile().getName();
            Files.add(FileName);

            Iterator<String> it = VP.getIterator();
            String currVarKey;
            while (it.hasNext()) {

                currVarKey = it.next();

                VariantContext var = VP.getVariant(currVarKey);
                List<String> names = var.getSampleNamesOrderedByName();
                if (var.isVariant()) {
                    NumVars++;
                    TotalNumVars++;
                }

                List<Allele> Alts = var.getAlternateAlleles();
                Allele ref = var.getReference();

                if (var.isSNP()) {
                    NumSNVs++;
                    TotalNumSNVs++;
                    TiCount += isTransition(ref, Alts);
                    TvCount += isTransversion(ref, Alts);
                    double[] temp = testTiTv(var, names, ref);
                    GenoTiCount += temp[0];
                    GenoTvCount += temp[1];
                } else if (var.isIndel()) {
                    List<Integer> indelLengths = var.getIndelLengths();
                    for (int length : indelLengths) {
                        InDels++;
                        TotalInDels++;
                        if (length > 0) {
                            Insertions++;
                            System.out.println("Insertion");
                        } else if (length < 0) {
                            Deletions++;
                            System.out.println("Deletion");

                        }
                    }
                } else if (var.isStructuralIndel()) {
                    StructVars++;
                    TotalStructVars++;
                }

                // if(var.getHetCount()>0){
                // NumHet = NumHet + var.getHetCount();
                // TotalNumHet = TotalNumHet + var.getHetCount();
                // }
                //
                // if(var.getHomRefCount()>0){
                // NumHomoRef = NumHomoRef + var.getHetCount();
                // TotalNumHomoRef = TotalNumHomoRef + var.getHetCount();
                // }
                //
                // if(var.getHomVarCount()>0){
                // NumHomoVar = NumHomoVar + var.getHetCount();
                // TotalNumHomoVar = TotalNumHomoVar + var.getHetCount();
                // }

                NumMultiAlts += AltCounter(Alts);

                double tempQualScore = var.getPhredScaledQual();
                if (tempQualScore >= 0) {
                    if (tempQualScore > MaxQScore)
                        MaxQScore = tempQualScore;
                    if (tempQualScore < MinQScore)
                        MinQScore = tempQualScore;
                    QualScore += tempQualScore;
                    TotalQualScore += tempQualScore;
                    TotalQVars++;
                    QVars++;
                } else {
                    QualError = "There was an error in the Qual formatting of: " + FileName + "  One or more variants had no Quality Score. It was excluded in the calculation.";
                }
                int TempDepth = getDepth(var, names);

                if (TempDepth >= 0) {
                    if (TempDepth > MaxDepth)
                        MaxDepth = TempDepth;
                    if (TempDepth < MinDepth)
                        MinDepth = TempDepth;
                    Depth += TempDepth;
                    TotalDepth += TempDepth;
                    TotalDPVars++;
                    DPVars++;
                } else {
                    DepthError = "There was an error in the Depth formatting of: " + FileName + "  One or more variants had no Read Depth. It was excluded in the calculation.";
                }
            }
            TotalNumMultiAlts += NumMultiAlts;
            if (TotalMinQScore > MinQScore)
                TotalMinQScore = MinQScore;
            if (TotalMaxQScore < MaxQScore)
                TotalMaxQScore = MaxQScore;
            if (TotalMinDepth > MinDepth)
                TotalMinDepth = MinDepth;
            if (TotalMaxDepth < MaxDepth)
                TotalMaxDepth = MaxDepth;
            TotalTiCount += TiCount;
            TotalTvCount += TvCount;
            TotalGenoTiCount += GenoTiCount;
            TotalGenoTvCount += GenoTvCount;
            TiTv = TiCount / TvCount;
            GenoTiTv = GenoTiCount / GenoTvCount;
            Depth = Depth / DPVars;
            QualScore = QualScore / QVars;

            if (!printMulti) {
                printFiles(files, count, Files, NumVars, NumSNVs, InDels, StructVars, NumHet, NumHomoRef, NumHomoVar, NumMultiAlts, QualScore, Depth, TiTv, GenoTiTv, MinDepth, MaxDepth, MinQScore, MaxQScore, printMulti, DepthError,
                        QualError);
            }
            // printToFile(FileName, numSamples, );
            count++;
        }
        TotalTiTv = TotalTiCount / TotalTvCount;
        TotalGenoTiTv = TotalGenoTiCount / TotalGenoTvCount;
        TotalDepth = TotalDepth / TotalDPVars;
        TotalQualScore = TotalQualScore / TotalQVars;

        if (printMulti) {
            printFiles(files, count, Files, TotalNumVars, TotalNumSNVs, TotalInDels, TotalStructVars, TotalNumHet, TotalNumHomoRef, TotalNumHomoVar, TotalNumMultiAlts, TotalQualScore, TotalDepth, TotalTiTv, TotalGenoTiTv, TotalMinDepth,
                    TotalMaxDepth, TotalMinQScore, TotalMaxQScore, printMulti, DepthError, QualError);
        }

    }

    private int getDepth(VariantContext var, List<String> names) {

        int count = 0;
        for (String s : names) {
            Genotype geno = var.getGenotypes().get(s);
            count += geno.getDP();

        }
        return count;
    }

    private double[] testTiTv(VariantContext var, List<String> names, Allele ref) {
        double countTi = 0;
        double countTv = 0;
        for (String s : names) {
            String refBase = ref.getBaseString();
            Genotype geno = var.getGenotypes().get(s);
            String gt = geno.getGenotypeString();
            String first = Character.toString(gt.charAt(0));
            String second = Character.toString(gt.charAt(2));
            countTi += checkTransition(refBase, first);
            countTi += checkTransition(refBase, second);
            countTv += checkTransversion(refBase, first);
            countTv += checkTransversion(refBase, second);
        }

        return new double[] { countTi, countTv };
    }

    private int checkTransition(String ref, String base) {
        int count = 0;
        if (base.equals("G") && ref.equals("A")) {
            count++;
        } else if (base.equals("A") && ref.equals("G")) {
            count++;
        } else if (base.equals("T") && ref.equals("C")) {
            count++;
        } else if (base.equals("C") && ref.equals("T")) {
            count++;
        }
        return count;
    }

    private int isTransition(Allele ref, List<Allele> alts) {
        String base;
        String refBase = ref.getBaseString();
        int count = 0;
        for (Allele a : alts) {
            base = a.getBaseString();
            count += checkTransition(refBase, base);
        }

        return count;
    }

    private int checkTransversion(String ref, String base) {
        int count = 0;
        if (base.equals("G") && ref.equals("C")) {
            count++;
        } else if (base.equals("C") && ref.equals("G")) {
            count++;
        } else if (base.equals("T") && ref.equals("A")) {
            count++;
        } else if (base.equals("A") && ref.equals("T")) {
            count++;
        }
        return count;
    }

    private int isTransversion(Allele ref, List<Allele> alts) {
        String base;
        String refBase = ref.getBaseString();
        int count = 0;
        for (Allele a : alts) {
            base = a.getBaseString();
            count += checkTransversion(refBase, base);
        }

        return count;
    }

    private int AltCounter(List<Allele> Alts) {

        if (Alts.size() > 1) {

            return 1;
        }
        return 0;
    }

    String roundDouble(double d) {
        DecimalFormat df = new DecimalFormat("#.##");
        return String.valueOf(df.format(d));
    }

    private int FindLength(int NumVars, int SNPs, int InDels, int StructVars, int NumHets, int NumHomoRef, int NumHomoVar, int NumMultiAlts, double QualScore, double Depth, double TiTv, double GenoTiTv, int MinDepth, int MaxDepth,
            double MinQScore, double MaxQScore, String title) {

        ArrayList<String> find = new ArrayList<String>();
        String numvars = Integer.toString(NumVars);
        find.add(numvars);
        String snps = Integer.toString(SNPs);
        find.add(snps);
        String indels = Integer.toString(InDels);
        find.add(indels);
        String structvars = Integer.toString(StructVars);
        find.add(structvars);
        String numhets = Integer.toString(NumHets);
        find.add(numhets);
        String numhomoref = Integer.toString(NumHomoRef);
        find.add(numhomoref);
        String numhomovar = Integer.toString(NumHomoVar);
        find.add(numhomovar);
        String nummultialts = Integer.toString(NumMultiAlts);
        find.add(nummultialts);
        String mindepth = Integer.toString(MinDepth);
        find.add(mindepth);
        String maxdepth = Integer.toString(MaxDepth);
        find.add(maxdepth);
        find.add(roundDouble(QualScore));
        find.add(roundDouble(MinQScore));
        find.add(roundDouble(MaxQScore));
        find.add(roundDouble(Depth));
        find.add(roundDouble(TiTv));
        find.add(roundDouble(GenoTiTv));
        find.add(title);

        int length = 0;
        for (String s : find) {
            if (s.length() > length)
                length = s.length();
            if (s.length() + 15 > title.length() && s != title)
                length = s.length() + 20;
        }
        return length;

    }

    private void printFiles(ArrayList<String> file, int count, ArrayList<String> FileName, int NumVars, int SNPs, int InDels, int StructVars, int NumHets, int NumHomoRef, int NumHomoVar, int NumMultiAlts, double QualScore, double Depth,
            double TiTv, double GenoTiTv, int MinDepth, int MaxDepth, double MinQScore, double MaxQScore, boolean printmulti, String DepthError, String QualError) {

        String newLine = System.getProperty("line.separator");

        String title;

        if (printmulti)
            title = "Summary of " + file.get(0) + ": " + FileName.get(0);
        else
            title = "Summary of " + file.get(count) + ": " + FileName.get(count);

        int length = FindLength(NumVars, SNPs, InDels, StructVars, NumHets, NumHomoRef, NumHomoVar, NumMultiAlts, QualScore, Depth, TiTv, GenoTiTv, MinDepth, MaxDepth, MinQScore, MaxQScore, title) + 5;

        char[] chars = new char[length + 1];
        Arrays.fill(chars, '-');
        String s = new String(chars);
        s = "+" + s + "+";

        char[] ch = new char[length + 3];
        Arrays.fill(ch, '=');
        String t = new String(ch);

        double snvPercent = (double) SNPs / (double) NumVars * 100;
        double InDelsPercent = (double) InDels / (double) NumVars * 100;
        double StructPercent = (double) StructVars / (double) NumVars * 100;

        int LeftColumn = 15;

        String leftalignFormatint = "|%-" + LeftColumn + "s%" + (length - LeftColumn) + "d |" + newLine;
        String leftalignFormatd = "|%-" + LeftColumn + "s%" + (length - LeftColumn) + ".2f |" + newLine;
        String rightalignFormati = "|%" + LeftColumn + "s%" + (length - LeftColumn) + "s |" + newLine;
        String rightalignFormatf = "|%" + LeftColumn + "s%" + (length - LeftColumn) + ".2f |" + newLine;
        String rightalignFormats = "|%" + LeftColumn + "s%" + (length - LeftColumn) + "s |" + newLine;
        String leftalignFormats = " %-" + (length--) + "s" + newLine;
        String leftAlignError = " %-" + length + "s" + newLine;

        if (printmulti) {
            System.out.format(t + newLine);
            int pos = 0;

            System.out.format(leftalignFormats, "");
            for (String vpfile : file) {
                if (pos > 0)
                    title = "           " + vpfile + ": " + FileName.get(pos);
                pos++;
                System.out.format(leftalignFormats, title);
            }
        } else {
            System.out.format(t + newLine);
            System.out.format(leftalignFormats, "");
            System.out.format(leftalignFormats, title);
        }
        System.out.format(leftalignFormats, "");
        System.out.format(t + newLine);
        System.out.format(newLine);
        System.out.format(s + newLine);
        System.out.format(leftalignFormatint, "TotalVars:", NumVars);
        System.out.format(s + newLine);
        System.out.format(rightalignFormati, "SNVs:      ", Integer.toString(SNPs) + " (" + roundDouble(snvPercent) + "%)");
        System.out.format(rightalignFormatf, "Ti/Tv:", TiTv);
        System.out.format(rightalignFormatf, "(Geno)Ti/Tv:", GenoTiTv);
        System.out.format(s + newLine);
        System.out.format(rightalignFormati, "INDELs:    ", Integer.toString(InDels) + " (" + roundDouble(InDelsPercent) + "%)");
        System.out.format(s + newLine);
        System.out.format(rightalignFormati, "StructVars:", Integer.toString(StructVars) + " (" + roundDouble(StructPercent) + "%)");
        System.out.format(s + newLine);
        System.out.format(leftalignFormatint, "MultiAlts:", NumMultiAlts);
        System.out.format(s + newLine);
        System.out.format(leftalignFormatd, "AvgQualScore:", QualScore);
        if (MinQScore == Integer.MAX_VALUE) {
            System.out.format(rightalignFormats, "MinQualScore:", "NaN");
        } else {
            System.out.format(rightalignFormatf, "MinQualScore:", MinQScore);
        }
        if (MaxQScore == -1) {
            System.out.format(rightalignFormats, "MaxQualScore:", "NaN");
        } else {
            System.out.format(rightalignFormatf, "MaxQualScore:", MaxQScore);
        }

        System.out.format(s + newLine);
        System.out.format(leftalignFormatd, "AvgDepth:", Depth);
        if (MinDepth == Integer.MAX_VALUE) {
            System.out.format(rightalignFormats, "MinDepth:", "NaN");
        } else {
            System.out.format(rightalignFormati, "MinDepth:", MinDepth);
        }
        if (MaxDepth == -1) {
            System.out.format(rightalignFormats, "MaxDepth:", "NaN");
        } else {
            System.out.format(rightalignFormati, "MaxDepth:", MaxDepth);
        }

        System.out.format(s + newLine);
        System.out.format(newLine + newLine);

        if (QualError.length() > 0)
            System.out.format(leftAlignError, QualError);
        if (DepthError.length() > 0)
            System.out.format(leftAlignError, DepthError);

    }

}
