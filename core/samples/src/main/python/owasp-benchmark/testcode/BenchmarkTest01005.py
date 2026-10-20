'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/pathtraver-01/BenchmarkTest01005', methods=['GET'])
	def BenchmarkTest01005_get():
		return BenchmarkTest01005_post()

	@app.route('/benchmark/pathtraver-01/BenchmarkTest01005', methods=['POST'])
	def BenchmarkTest01005_post():
		RESPONSE = ""

		parts = request.path.split("/")
		param = parts[1]
		if not param:
			param = ""

		map96560 = {}
		map96560['keyA-96560'] = 'a-Value'
		map96560['keyB-96560'] = param
		map96560['keyC'] = 'another-Value'
		bar = map96560['keyB-96560']

		import platform
		import codecs
		import helpers.utils
		from urllib.parse import urlparse
		from urllib.request import url2pathname

		startURIslashes = ""

		if platform.system() == "Windows":
			startURIslashes = "/"
		else:
			startURIslashes = "//"

		try:
			fileURI = urlparse("file:" + startURIslashes + helpers.utils.TESTFILES_DIR.replace('\\', '/').replace(' ', '_') + bar)
			fileTarget = codecs.open(f'{helpers.utils.TESTFILES_DIR}/{bar}','r','utf-8')

			RESPONSE += (
				f"Access to file: \'{escape_for_html(fileTarget.name)}\' created."
			)

			RESPONSE += (
				" And file already exists."
			)
		except FileNotFoundError:
			RESPONSE += (
				" But file doesn't exist yet."
			)
		except IOError:
			pass

		return RESPONSE

