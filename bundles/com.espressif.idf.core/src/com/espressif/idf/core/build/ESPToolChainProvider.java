/*******************************************************************************
 * Copyright 2018-2019 Espressif Systems (Shanghai) PTE LTD. All rights reserved.
 * Use is subject to license terms.
 *******************************************************************************/

package com.espressif.idf.core.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.build.gcc.core.GCCToolChain.GCCInfo;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.IToolChainManager;
import org.eclipse.cdt.core.build.IToolChainProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;

import com.aptana.core.ShellExecutable;
import com.aptana.core.util.ProcessRunner;
import com.espressif.idf.core.IDFConstants;
import com.espressif.idf.core.IDFCorePlugin;
import com.espressif.idf.core.IDFEnvironmentVariables;
import com.espressif.idf.core.logging.Logger;
import com.espressif.idf.core.util.IDFUtil;
import com.espressif.idf.core.util.StringUtil;

/**
 * @author Kondal Kolipaka <kondal.kolipaka@espressif.com>
 *
 */
public class ESPToolChainProvider implements IToolChainProvider
{

	public static final String ID = "com.espressif.idf.core.esp.toolchainprovider"; //$NON-NLS-1$
	private static final Pattern gccPattern = Pattern.compile("xtensa-esp32-elf-gcc(\\.exe)?"); //$NON-NLS-1$

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public void init(IToolChainManager manager) throws CoreException
	{
		List<String> paths = new ArrayList<String>();
		String idfToolsExportPath = getIdfToolsExportPath();
		if (idfToolsExportPath != null)
		{
			paths.add(idfToolsExportPath);
		}
		else
		{
			paths = getAllPaths();
		}

		for (String path : paths)
		{
			for (String dirStr : path.split(File.pathSeparator))
			{
				File dir = new File(dirStr);
				if (dir.isDirectory())
				{
					for (File file : dir.listFiles())
					{
						if (file.isDirectory())
						{
							continue;
						}
						Matcher matcher = gccPattern.matcher(file.getName());
						if (matcher.matches())
						{
							try
							{
								GCCInfo info = new GCCInfo(file.toString());
								if (info.target != null && info.version != null)
								{
									String[] tuple = info.target.split("-"); //$NON-NLS-1$
									if (tuple.length > 2) // xtensa-esp32-elf
									{

										ESPToolChain gcc = new ESPToolChain(this, file.toPath());
										try
										{
											if (manager.getToolChain(gcc.getTypeId(), gcc.getId()) == null)
											{
												// Only add if another provider hasn't already added it
												if (matcher.matches())
												{
													manager.addToolChain(gcc);
												}
											}
										}
										catch (CoreException e)
										{
											CCorePlugin.log(e.getStatus());
										}
									}
								}
							}
							catch (IOException e)
							{
								Logger.log(IDFCorePlugin.getPlugin(), e);
							}
						}
					}
				}
			}
		}

	}

	protected List<String> getAllPaths()
	{
		List<String> paths = new ArrayList<String>();

		String path = new IDFEnvironmentVariables().getEnvValue(IDFEnvironmentVariables.PATH);
		if (!StringUtil.isEmpty(path))
		{
			paths.add(path);
		}

		path = System.getenv(IDFEnvironmentVariables.PATH);
		if (!StringUtil.isEmpty(path))
		{
			paths.add(path);
		}

		path = ShellExecutable.getEnvironment().get(IDFEnvironmentVariables.PATH);
		if (StringUtil.isEmpty(path))
		{
			paths.add(path);
		}

		return paths;
	}

	protected String getIdfToolsExportPath()
	{
		String idf_path = IDFUtil.getIDFPath();
		String tools_path = idf_path + IPath.SEPARATOR + IDFConstants.TOOLS_FOLDER + IPath.SEPARATOR
				+ IDFConstants.IDF_TOOLS_SCRIPT;

		Logger.log("idf_tools.py path: " + tools_path);
		if (!new File(tools_path).exists())
		{
			Logger.log("idf_tools.py path doesn't exist");
			return null;
		}

		try
		{
			IStatus idf_tools_export_status = new ProcessRunner().runInBackground(tools_path,
					IDFConstants.TOOLS_EXPORT_CMD, IDFConstants.TOOLS_EXPORT_CMD_FORMAT_VAL);
			if (idf_tools_export_status != null && idf_tools_export_status.isOK())
			{
				String message = idf_tools_export_status.getMessage();
				Logger.log("idf_tools.py export output: " + message);
				if (message != null)
				{
					String[] exportEntries = message.split("\n"); //$NON-NLS-1$
					for (String entry : exportEntries)
					{
						String[] keyValue = entry.split("="); //$NON-NLS-1$
						if (keyValue.length == 2 && keyValue[0].equals(IDFEnvironmentVariables.PATH)) // 0 - key, 1 - value
						{
							Logger.log("PATH: " + keyValue[1]);
							return keyValue[1];
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			Logger.log(IDFCorePlugin.getPlugin(), e);
		}
		return null;
	}

	protected Map<String, String> getEnvironment(IPath location)
	{
		return ShellExecutable.getEnvironment(location);
	}

}