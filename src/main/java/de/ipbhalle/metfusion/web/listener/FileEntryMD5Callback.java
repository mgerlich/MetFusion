/**
 * created by Michael Gerlich, Oct 17, 2011 - 1:47:46 PM
 */

package de.ipbhalle.metfusion.web.listener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;

import org.apache.commons.codec.binary.Hex;
import org.icefaces.ace.component.fileentry.FileEntryCallback;
import org.icefaces.ace.component.fileentry.FileEntryResults;
import org.icefaces.ace.component.fileentry.FileEntryResults.FileInfo;
import org.icefaces.ace.component.fileentry.FileEntryStatus;

import de.ipbhalle.metfusion.utilities.icefaces.TutorialMessageUtils;

@RequestScoped
@ManagedBean
public class FileEntryMD5Callback implements FileEntryCallback {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private MessageDigest digest;
	private boolean md5NotFound = false;

	@Override
	public void begin(FileInfo fileInfo) {
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			md5NotFound = true;
		}
	}

	@Override
	// When FileEntryCallback ends for a file
	public void end(FileEntryResults.FileInfo fileEntryInfo) {
		// If the file upload was completed properly
		if (md5NotFound)
			fileEntryInfo.updateStatus(new EncodingNotFoundUploadStatus(),
					true, true);

		if (fileEntryInfo.getStatus().isSuccess()) {
			fileEntryInfo.updateStatus(new EncodingSuccessStatus(), false);
		}
	}

	@Override
	public void write(int i) {
		if (!md5NotFound)
			digest.update((byte) i);
	}

	@Override
	public void write(byte[] bytes, int offset, int length) {
		if (!md5NotFound)
			digest.update(bytes, offset, length);
	}

	// Assistance method to convert digested bytes to hex string
	public String getHash() {
		return String.valueOf(Hex.encodeHex(digest.digest()));
	}

	private class EncodingNotFoundUploadStatus implements FileEntryStatus {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public boolean isSuccess() {
			return false;
		}

		public FacesMessage getFacesMessage(FacesContext facesContext,
				UIComponent uiComponent, FileEntryResults.FileInfo fileInfo) {
			return new FacesMessage(
					FacesMessage.SEVERITY_ERROR,
					TutorialMessageUtils.getMessage("content.callback.encode.fail.message"),
					TutorialMessageUtils.getMessage("content.callback.encode.fail.detail"));
		}
	}

	private class EncodingSuccessStatus implements FileEntryStatus {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public boolean isSuccess() {
			return true;
		}

		public FacesMessage getFacesMessage(FacesContext facesContext,
				UIComponent uiComponent, FileEntryResults.FileInfo fileInfo) {
			return new FacesMessage(FacesMessage.SEVERITY_INFO,
					TutorialMessageUtils.getMessage("content.callback.result.message"),
					getHash());
		}
	}

}
