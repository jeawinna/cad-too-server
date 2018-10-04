package com.bplead.cad.util;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.DataContent;
import com.bplead.cad.bean.SimpleDocument;
import com.bplead.cad.bean.SimpleFolder;
import com.bplead.cad.bean.SimplePdmLinkProduct;
import com.bplead.cad.bean.io.CAD;
import com.bplead.cad.bean.io.CAPP;
import com.bplead.cad.bean.io.Document;

import priv.lee.cad.bean.HandleResult;
import priv.lee.cad.util.Assert;
import wt.doc.WTDocument;
import wt.fc.QueryResult;
import wt.folder.Folder;
import wt.folder.FolderHelper;
import wt.folder.SubFolder;
import wt.inf.container.ContainerSpec;
import wt.inf.container.WTContainerHelper;
import wt.inf.container.WTContainerRef;
import wt.method.RemoteAccess;
import wt.org.WTPrincipal;
import wt.org.WTPrincipalReference;
import wt.org.WTUser;
import wt.part.WTPart;
import wt.pdmlink.PDMLinkProduct;
import wt.pom.Transaction;
import wt.session.SessionHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;

public class ServerUtils implements RemoteAccess, Serializable {

	private static final String DEFAULT_FOLDER = "/Default";
	public static final String EXCEPTION_RB = "com.bplead.cad.resource.CADToolExceptionRB_zh_CN";
	private static final Locale locale = Locale.CHINESE;
	private static final Logger logger = Logger.getLogger(ServerUtils.class);
	private static final String NAVIGATION_RB = "com.ptc.core.ui.navigationRB";
	private static final long serialVersionUID = 3944141455864195993L;
	private static final String WELCOME = "WELCOME";

	public static HandleResult<Boolean> checkin(Document document) {
		HandleResult<Boolean> result = null;
		Transaction tran = null;
		try {
			tran = new Transaction();
			tran.start();

			Assert.notNull(document, "Error to get document");
			Assert.notNull(document.getObject(), "Error to get object of document");

			// seach describe WTPart
			Serializable object = document.getObject();
			List<WTPart> parts = null;
			if (CAD.class.isInstance(object)) {
				parts = PartUtils.search((CAD) document.getObject());
			} else {
				parts = PartUtils.update((CAPP) document.getObject(), DocumentUtils.updated(document.getType()));
			}

			// save or update WTDocument
			WTDocument doc = DocumentUtils.saveOrUpdate(document);

			// create describelink between WTPart and WTDocument
			result = HandleResult.toSuccessedResult(PartDocumentUtils.saveOrUpdateDescribeLinks(doc, parts));

			tran.commit();
			tran = null;
		} catch (Exception e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} finally {
			if (tran != null) {
				tran.rollback();
			}

			if (result == null) {
				result = HandleResult.toUnExpectedResult();
			}
		}
		return result;
	}

	public static HandleResult<DataContent> checkoutAndDownload(List<SimpleDocument> documents) {
		HandleResult<DataContent> result = null;
		Transaction tran = null;
		try {
			tran = new Transaction();
			tran.start();

			result = HandleResult.toSuccessedResult(DocumentUtils.checkoutAndDownload4Zip(documents));

			tran.commit();
			tran = null;
		} catch (Exception e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} finally {
			if (tran != null) {
				tran.rollback();
			}

			if (result == null) {
				result = HandleResult.toUnExpectedResult();
			}
		}
		return result;
	}

	private static List<SimpleFolder> findFolders(Folder folder, WTContainerRef ref) throws WTException {
		List<SimpleFolder> folders = new ArrayList<SimpleFolder>();
		if (folder == null || ref == null) {
			return folders;
		}

		QueryResult result = FolderHelper.service.findSubFolders(folder);
		while (result.hasMoreElements()) {
			Object object = result.nextElement();
			if (object instanceof SubFolder) {
				SubFolder subFolder = (SubFolder) object;
				SimpleFolder simpleFolder = new SimpleFolder(CommonUtils.getPersistableOid(subFolder),
						subFolder.getName());
				simpleFolder.setChildren(findFolders(subFolder, ref));
				folders.add(simpleFolder);
			}
		}
		return folders;
	}

	public static HandleResult<SimpleFolder> getSimpleFolders(SimplePdmLinkProduct product) {
		if (product == null) {
			return HandleResult.toErrorResult(null, "Product is required");
		}

		HandleResult<SimpleFolder> result = null;
		try {
			PDMLinkProduct pdmLinkProduct = CommonUtils.getPersistable(product.getOid(), PDMLinkProduct.class);
			WTContainerRef ref = WTContainerRef.newWTContainerRef(pdmLinkProduct);

			Folder folder = FolderHelper.service.getFolder(DEFAULT_FOLDER, ref);
			SimpleFolder rootFolder = new SimpleFolder(CommonUtils.getPersistableOid(folder), folder.getName());
			rootFolder.setChildren(findFolders(folder, ref));
			result = HandleResult.toSuccessedResult(rootFolder);
		} catch (Exception e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} finally {
			if (result == null) {
				result = HandleResult.toUnExpectedResult();
			}
		}
		return result;
	}

	public static HandleResult<List<SimplePdmLinkProduct>> getSimplePdmLinkProducts() {
		HandleResult<List<SimplePdmLinkProduct>> result = null;
		try {
			List<SimplePdmLinkProduct> products = new ArrayList<SimplePdmLinkProduct>();
			WTPrincipal principal = SessionHelper.manager.getPrincipal();
			ContainerSpec cs = new ContainerSpec(PDMLinkProduct.class);
			cs.setUser(WTPrincipalReference.newWTPrincipalReference(principal));
			cs.setMembershipState(256);
			QueryResult qr = WTContainerHelper.service.getContainers(cs);
			while (qr.hasMoreElements()) {
				PDMLinkProduct product = (PDMLinkProduct) qr.nextElement();
				products.add(new SimplePdmLinkProduct(CommonUtils.getPersistableOid(product), product.getName()));
			}
			logger.debug("principal:" + principal + ",products:" + products);
			result = HandleResult.toSuccessedResult(products);
		} catch (WTException e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} catch (WTPropertyVetoException e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} finally {
			if (result == null) {
				result = HandleResult.toUnExpectedResult();
			}
		}
		return result;
	}

	public static HandleResult<String> getWelcomeMessage() {
		HandleResult<String> result = null;
		try {
			WTUser user = (WTUser) SessionHelper.getPrincipal();
			ResourceBundle bundle = ResourceBundle.getBundle(NAVIGATION_RB, locale);
			result = HandleResult
					.toSuccessedResult(MessageFormat.format(bundle.getString(WELCOME), user.getFullName()));
		} catch (Exception e) {
			result = HandleResult.toErrorResult(e);
			e.printStackTrace();
		} finally {
			if (result == null) {
				result = HandleResult.toUnExpectedResult();
			}
		}
		return result;
	}

	public static HandleResult<List<SimpleDocument>> search(String number, String name) {
		return HandleResult.toSuccessedResult(DocumentUtils.search(number, name));
	}
}
