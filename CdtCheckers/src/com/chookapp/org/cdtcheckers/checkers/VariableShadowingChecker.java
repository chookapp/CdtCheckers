/*******************************************************************************
 * Copyright (c) 2010 Gil Barash 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Gil Barash  - Initial implementation
 *******************************************************************************/
package com.chookapp.org.cdtcheckers.checkers;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.codan.core.model.ICheckerWithPreferences;
import org.eclipse.cdt.codan.core.model.IProblemLocation;
import org.eclipse.cdt.codan.core.model.IProblemWorkingCopy;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.internal.core.resources.ResourceLookup; // I know this is not recommended, will fix later
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

public class VariableShadowingChecker extends AbstractIndexAstChecker 
									  implements ICheckerWithPreferences {

	public static final String ER_ID = "com.chookapp.org.checkers.VariableShadowingProblem"; //$NON-NLS-1$
	public static final String PARAM_MARK_SHADOWED = "param_mark_shadowed"; //$NON-NLS-1$
	public static final String PARAM_MARK_SHADOWED_DIFFERENT_FILE = "param_mark_shadowed_different_file"; //$NON-NLS-1$
	
	private IASTTranslationUnit _ast;
	private IIndex _index;
	private Boolean _markShadowed; // Mark also the variables shadowed by ones in the lower scopes
	private Boolean _markShadowedDifferentFile; // Mark the shadowed variables even if the're in a different file
	private Set<String> _problemsMap;	
	
	/**
	 * This visitor looks for variable declarations.
	 */
	class VariableDeclarationVisitor extends ASTVisitor 
	{

		VariableDeclarationVisitor() 
		{
			shouldVisitDeclarators = true;
		}

		@Override
		public int visit(IASTDeclarator declarator) 
		{
			try {
				processDeclarator( declarator );
			} catch (DOMException e) {
				e.printStackTrace();
			} catch (CoreException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return PROCESS_CONTINUE;
		}
		
		@SuppressWarnings("restriction")
		private synchronized IASTTranslationUnit getNameAst( IIndexName iName ) throws CoreException, InterruptedException 
		{
			IFile ifile = ResourceLookup.selectFileForLocation( new Path(iName.getFileLocation().getFileName()),
																getProject() );
			if( ifile == null )
				return null;
			
			ICElement celement = CoreModel.getDefault().create(ifile);
			if (!(celement instanceof ITranslationUnit))
				return null; // not a C/C++ file
			
			ITranslationUnit tu = (ITranslationUnit) celement;
			IIndex index = CCorePlugin.getIndexManager().getIndex(tu.getCProject());
			// lock the index for read access
			index.acquireReadLock();
			try {
				// create index based ast
				IASTTranslationUnit ast = tu.getAST(index, ITranslationUnit.AST_SKIP_INDEXED_HEADERS);
				if (ast == null)
					return null;//
				return ast;
			} finally {
				index.releaseReadLock();
			}
		}
		
		private IASTName indexNameToASTName( IIndexName iName ) throws CoreException, InterruptedException 
		{
			IASTName name;
			name = _ast.getNodeSelector(iName.getFileLocation().getFileName()).findEnclosingName(iName.getNodeOffset(), iName.getNodeLength());
			if( name != null ) 
				return name;
			
			/* try a different AST... */
			
			IASTTranslationUnit ast = getNameAst(iName);
			if( ast == null )
				return null;
			
			name = ast.getNodeSelector(iName.getFileLocation().getFileName()).findEnclosingName(iName.getNodeOffset(), iName.getNodeLength());
			
			return name;
		}
	
		private void processDeclerationShadowing(IASTDeclarator shadowing,
												 IIndexName shadowed) throws CoreException, InterruptedException 
		{
			
			IASTName shadowedAstName = indexNameToASTName (shadowed); 
			if( shadowedAstName != null ) {
				processDeclerationShadowing( shadowing, shadowedAstName );
			} else {
				myReportProblem(ER_ID, shadowing, shadowing.getName(), 
						Messages.VariableShadowingChecker_shadowing, 
						nodeFullName(shadowed), nodeLocation(shadowed));
				
				if( _markShadowed ) {
					IASTFileLocation shadowedFileLoc = shadowed.getFileLocation();
					if( shadowedFileLoc.getFileName().equals(shadowing.getFileLocation().getFileName()) ||
					    _markShadowedDifferentFile )
					{
						@SuppressWarnings("restriction")
						IFile ifile = ResourceLookup.selectFileForLocation( new Path(shadowedFileLoc.getFileName()), getProject() );
						if( ifile != null ) {
							IProblemLocation probLoc = createProblemLocation( ifile, shadowed.getNodeOffset(), shadowed.getNodeOffset() + shadowed.getNodeLength() );
							reportProblem(ER_ID, probLoc, shadowed.toString(), 
									Messages.VariableShadowingChecker_shadowed_by, 
									nodeFullName(shadowing.getName()), 
									nodeLocation(shadowing.getName()));
						}
					}
				}
			}
			
		}		
		
		private void myReportProblem(String erId, IASTNode node1,
				IASTName node1Name, String action, String node2Name, String node2Location) 
		{
			String node1Location = node1.getFileLocation().toString();
			String key = node1Location + node1Name.toString() + action + node2Name + node2Location;
			
			if(_problemsMap.add(key))
				reportProblem(erId, node1, node1Name, action, node2Name, node2Location);			
		}
		
		private void myReportProblem(String erId, IProblemLocation node1Location,
				String node1Name, String action, String node2Name, String node2Location) 
		{
			String key = node1Location.getFile().toString() + node1Location.getStartingChar() + node1Name.toString() + action + node2Name + node2Location;
			if(_problemsMap.add(key))
				reportProblem(erId, node1Location, node1Name, action, node2Name, node2Location);
		}

		private void processDeclerationShadowing(IASTDeclarator shadowing,
				IASTName shadowed) 
		{
	
			IASTFileLocation shadowedFileLoc = shadowed.getFileLocation();
			
			// assert that the file location is not the same for both
			Assert.isTrue(!shadowing.getFileLocation().equals(shadowedFileLoc));
			
			myReportProblem(ER_ID, shadowing, shadowing.getName(), 
					Messages.VariableShadowingChecker_shadowing, 
					nodeFullName(shadowed), nodeLocation(shadowed));
			if( _markShadowed )
			{
				if(shadowedFileLoc.getFileName().equals(shadowing.getFileLocation().getFileName()))
				{
					myReportProblem(ER_ID, shadowed, shadowed, 
							Messages.VariableShadowingChecker_shadowed_by, 
							nodeFullName(shadowing.getName()), 
							nodeLocation(shadowing.getName()));
				}
				else if( _markShadowedDifferentFile )
				{
					@SuppressWarnings("restriction")
					IFile ifile = ResourceLookup.selectFileForLocation( new Path(shadowedFileLoc.getFileName()), getProject() );
					if(ifile != null)
					{
						IProblemLocation probLoc = createProblemLocation( ifile, shadowedFileLoc.getNodeOffset(), shadowedFileLoc.getNodeOffset() + shadowedFileLoc.getNodeLength() );
						myReportProblem(ER_ID, probLoc, shadowed.toString(),
								Messages.VariableShadowingChecker_shadowed_by, 
								nodeFullName(shadowing.getName()), 
								nodeLocation(shadowing.getName()));
					}
				}
			}
		
		}	
		

		@SuppressWarnings("restriction")
		private String getNodeFileName(IASTFileLocation fileLoc) 
		{
			String fileName = fileLoc.getFileName();
			IFile ifile = ResourceLookup.selectFileForLocation( new Path(fileName), getProject() );
			if( ifile != null )
				fileName = ifile.toString();
			return fileName;
		}
		
		private String nodeFullName(IASTName name) 
		{
			String ret = ""; //$NON-NLS-1$
			
			try {
				IBinding owner;
				owner = name.resolveBinding().getOwner();
				ret = owner.getName() + "::"; //$NON-NLS-1$
			} catch (NullPointerException e ) {				
			}
			
			ret += name;
			return ret;
		}
		
		private String nodeFullName(IIndexName name) 
		{
			return name.toString();		
		}
		
		private String nodeLocation(IASTName name) 
		{
			IASTFileLocation fileLoc = name.getFileLocation();
			return getNodeFileName(fileLoc) + 
				Messages.VariableShadowingChecker_at_line + 
				fileLoc.getStartingLineNumber();
		}

		private String nodeLocation(IIndexName name) 
		{
			IASTFileLocation fileLoc = name.getFileLocation();
			return getNodeFileName(fileLoc) + 
				Messages.VariableShadowingChecker_at_byte + 
				name.getNodeOffset();
		}

		private void processDeclarator( IASTDeclarator declarator ) throws DOMException, CoreException, InterruptedException 
		{
			IBinding binding = declarator.getName().getBinding();
			if(binding == null)
				return;
			
			IScope scope = null;
			try
			{
				scope = binding.getScope();
				if(scope == null)
					return;
				
				scope = scope.getParent();
			}
			catch(DOMException e)
			{
				return;
			}			
			
			while( scope != null ) {
	
				IBinding[] scopeBindings = scope.find(declarator.getName().toString());
				
				for( IBinding scopeBinding : scopeBindings ) {
					if( scopeBinding != null && (scopeBinding instanceof IVariable) ) {

						IASTName[] declNames= _ast.getDeclarationsInAST(scopeBinding);
						if( declNames != null && declNames.length != 0 ) {
							processDeclerationShadowing( declarator, declNames[0] );
						} else { // not found in AST, look in index
							IIndexName[] indexNames = _index.findDeclarations(scopeBinding);
							if( indexNames != null && indexNames.length != 0 )
								processDeclerationShadowing( declarator, indexNames[0] );
						}
						
						break; // We found the variable we were looking for...
						
					} // if is variable  
				} // for over "scopeBindings"
				
				scope = scope.getParent();
			}
		}
		
	}

	/************************************************
	 * "VariableShadowingChecker" functions...
	 ************************************************/
	public VariableShadowingChecker() 
	{
	}
	
	public void initPreferences(IProblemWorkingCopy problem) 
	{
		super.initPreferences(problem);
		addPreference(problem, PARAM_MARK_SHADOWED,
				"Mark also the variables shadowed by the ones in the lower scopes",
				Boolean.TRUE);
		addPreference(problem, PARAM_MARK_SHADOWED_DIFFERENT_FILE,
				"Mark the shadowed variables even if the're in a different file",
				Boolean.FALSE);
	}
	
	public void processAst(IASTTranslationUnit ast) 
	{
		_problemsMap = new HashSet<String>();
		
		_markShadowed = (Boolean) getPreference(
				getProblemById(ER_ID, getFile()), PARAM_MARK_SHADOWED);
		_markShadowedDifferentFile = (Boolean) getPreference(
				getProblemById(ER_ID, getFile()), PARAM_MARK_SHADOWED_DIFFERENT_FILE);
		
		_ast = ast;
		_index = ast.getIndex();

		VariableDeclarationVisitor visitor = new VariableDeclarationVisitor();
		ast.accept(visitor);		
		
	}

}