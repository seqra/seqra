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

	@app.route('/benchmark/redirect-00/BenchmarkTest00983', methods=['GET'])
	def BenchmarkTest00983_get():
		return BenchmarkTest00983_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00983', methods=['POST'])
	def BenchmarkTest00983_post():
		RESPONSE = ""

		import urllib.parse
		
		query_string = request.query_string.decode('utf-8')
		paramLoc = query_string.find("BenchmarkTest00983" + '=')
		if paramLoc == -1:
			return f"request.query_string did not contain expected parameter \'{"BenchmarkTest00983"}\'."
		param = query_string[paramLoc + len("BenchmarkTest00983") + 1:]
		ampLoc = param.find('&')
		if ampLoc != -1:
			param = param[:ampLoc]
		
		param = urllib.parse.unquote_plus(param)

		map15322 = {}
		map15322['keyA-15322'] = 'a-Value'
		map15322['keyB-15322'] = param
		map15322['keyC'] = 'another-Value'
		bar = map15322['keyB-15322']

		import flask
		import urllib.parse

		try:
			url = urllib.parse.urlparse(bar)
			if url.netloc not in ['google.com'] or url.scheme != 'https':
				RESPONSE += (
					'Invalid URL.'
				)
				return RESPONSE
		except:
			RESPONSE += (
				'Error parsing URL.'
			)
			return RESPONSE

		return flask.redirect(bar)

		return RESPONSE

