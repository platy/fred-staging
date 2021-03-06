/* This code is part of Freenet. It is distributed under the GNU General
* Public License, version 2 (or at your option any later version). See
* http://www.gnu.org/ for further details of the GPL. */
package freenet.support.compress;

import java.io.IOException;

import com.db4o.ObjectContainer;

import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;

/**
 * A data compressor. Contains methods to get all data compressors.
 * This is for single-file compression (gzip, bzip2) as opposed to archives.
 */
public interface Compressor {

	public enum COMPRESSOR_TYPE implements Compressor {
		// WARNING: THIS CLASS IS STORED IN DB4O -- THINK TWICE BEFORE ADD/REMOVE/RENAME FIELDS
		// They will be tried in order: put the less resource consuming first
		GZIP("GZIP", new GzipCompressor(), (short) 0),
		BZIP2("BZIP2", new Bzip2Compressor(), (short) 1),
		LZMA("LZMA", new LZMACompressor(), (short)2);
		
		public final String name;
		public final Compressor compressor;
		public final short metadataID;
		
		COMPRESSOR_TYPE(String name, Compressor c, short metadataID) {
			this.name = name;
			this.compressor = c;
			this.metadataID = metadataID;
		}
		
		public static COMPRESSOR_TYPE getCompressorByMetadataID(short id) {
			COMPRESSOR_TYPE[] values = values();
			for(COMPRESSOR_TYPE current : values)
				if(current.metadataID == id)
					return current;
			return null;
		}

		public Bucket compress(Bucket data, BucketFactory bf, long maxReadLength, long maxWriteLength) throws IOException, CompressionOutputSizeException {
			if(compressor == null) {
				// DB4O VOODOO! See below.
				if(name != null) return getOfficial().compress(data, bf, maxReadLength, maxWriteLength);
			}
			return compressor.compress(data, bf, maxReadLength, maxWriteLength);
		}

		public Bucket decompress(Bucket data, BucketFactory bucketFactory, long maxLength, long maxEstimateSizeLength, Bucket preferred) throws IOException, CompressionOutputSizeException {
			if(compressor == null) {
				// DB4O VOODOO! See below.
				if(name != null) return getOfficial().decompress(data, bucketFactory, maxLength, maxEstimateSizeLength, preferred);
			}
			return compressor.decompress(data, bucketFactory, maxLength, maxEstimateSizeLength, preferred);
		}

		public int decompress(byte[] dbuf, int i, int j, byte[] output) throws CompressionOutputSizeException {
			if(compressor == null) {
				// DB4O VOODOO! See below.
				if(name != null) return getOfficial().decompress(dbuf, i, j, output);
			}
			return compressor.decompress(dbuf, i, j, output);
		}
		
		// DB4O VOODOO!
		// Copies of the static fields get stored into the database.
		// Really the solution is probably to store the codes only.
		
		private Compressor getOfficial() {
			if(name.equals("GZIP")) return GZIP;
			if(name.equals("BZIP2")) return BZIP2;
			if(name.equals("LZMA")) return LZMA;
			return null;
		}

		public boolean objectCanDeactivate(ObjectContainer container) {
			// Do not deactivate the official COMPRESSOR_TYPE's.
			if(isOfficial()) return false;
			return true;
		}
		
		public boolean objectCanActivate(ObjectContainer container) {
			// Do not activate the official COMPRESSOR_TYPE's.
			if(isOfficial()) return false;
			return true;
		}
		
		public boolean isOfficial() {
			return this == GZIP || this == BZIP2 || this == LZMA;
		}
		
	}

	/**
	 * Compress the data.
	 * @param data The bucket to read from.
	 * @param bf The means to create a new bucket.
	 * @param maxReadLength The maximum number of bytes to read from the input bucket.
	 * @param maxWriteLength The maximum number of bytes to write to the output bucket. If this is exceeded, throw a CompressionOutputSizeException.
	 * @return The compressed data.
	 * @throws IOException If an error occurs reading or writing data.
	 * @throws CompressionOutputSizeException If the compressed data is larger than maxWriteLength. 
	 */
	public abstract Bucket compress(Bucket data, BucketFactory bf, long maxReadLength, long maxWriteLength) throws IOException, CompressionOutputSizeException;

	/**
	 * Decompress data.
	 * @param data The data to decompress.
	 * @param bucketFactory A BucketFactory to create a new Bucket with if necessary.
	 * @param maxLength The maximum length to decompress (we throw if more is present).
	 * @param maxEstimateSizeLength If the data is too big, and this is >0, read up to this many bytes in order to try to get the data size.
	 * @param preferred A Bucket to use instead. If null, we allocate one from the BucketFactory.
	 * @return
	 * @throws IOException
	 * @throws CompressionOutputSizeException
	 */
	public abstract Bucket decompress(Bucket data, BucketFactory bucketFactory, long maxLength, long maxEstimateSizeLength, Bucket preferred) throws IOException, CompressionOutputSizeException;

	/** Decompress in RAM only.
	 * @param dbuf Input buffer.
	 * @param i Offset to start reading from.
	 * @param j Number of bytes to read.
	 * @param output Output buffer.
	 * @throws DecompressException 
	 * @throws CompressionOutputSizeException 
	 * @returns The number of bytes actually written.
	 */
	public abstract int decompress(byte[] dbuf, int i, int j, byte[] output) throws CompressionOutputSizeException;
}
