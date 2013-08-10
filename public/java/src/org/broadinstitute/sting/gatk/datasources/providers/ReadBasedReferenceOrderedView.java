/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.sting.gatk.datasources.providers;

import com.google.java.contract.Ensures;
import com.google.java.contract.Requires;
import net.sf.picard.util.PeekableIterator;
import net.sf.samtools.SAMRecord;
import org.broadinstitute.sting.gatk.datasources.reads.ReadShard;
import org.broadinstitute.sting.gatk.datasources.rmd.ReferenceOrderedDataSource;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.refdata.utils.LocationAwareSeekableRODIterator;
import org.broadinstitute.sting.gatk.refdata.utils.RODRecordList;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.GenomeLocParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** a ROD view for reads. This provides the Read traversals a way of getting a RefMetaDataTracker */
public class ReadBasedReferenceOrderedView extends IntervalReferenceOrderedView {
    public ReadBasedReferenceOrderedView(final ShardDataProvider provider) {
        super(provider, provider.hasReferenceOrderedData() ? ((ReadShard)provider.getShard()).getReadsSpan() : null);
    }

    /**
     * create a RefMetaDataTracker given the current read
     *
     * @param rec the read
     *
     * @return a RefMetaDataTracker for the read, from which you can get ROD -> read alignments
     */
    @Requires("rec != null")
    @Ensures("result != null")
    public RefMetaDataTracker getReferenceOrderedDataForRead(final SAMRecord rec) {
        if ( rec.getReadUnmappedFlag() )
            return RefMetaDataTracker.EMPTY_TRACKER;
        else {
            final GenomeLoc readSpan = genomeLocParser.createGenomeLoc(rec);
            trimCurrentFeaturesToLoc(readSpan);
            return getReferenceOrderedDataForInterval(readSpan);
        }
    }
}

