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

	@app.route('/benchmark/redirect-00/BenchmarkTest00069', methods=['GET'])
	def BenchmarkTest00069_get():
		response = make_response(render_template('web/redirect-00/BenchmarkTest00069.html'))
		response.set_cookie('BenchmarkTest00069', 'http%3A%2F%2Flocalhost%3A5000%2F',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00069_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00069', methods=['POST'])
	def BenchmarkTest00069_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00069", "noCookieValueSupplied"))

		import configparser
		
		bar = 'safe!'
		conf74259 = configparser.ConfigParser()
		conf74259.add_section('section74259')
		conf74259.set('section74259', 'keyA-74259', 'a-Value')
		conf74259.set('section74259', 'keyB-74259', param)
		bar = conf74259.get('section74259', 'keyB-74259')

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

