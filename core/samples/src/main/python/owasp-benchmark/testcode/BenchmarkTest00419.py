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

	@app.route('/benchmark/redirect-00/BenchmarkTest00419', methods=['GET'])
	def BenchmarkTest00419_get():
		return BenchmarkTest00419_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00419', methods=['POST'])
	def BenchmarkTest00419_post():
		RESPONSE = ""

		param = ""
		for name in request.form.keys():
			if "BenchmarkTest00419" in request.form.getlist(name):
				param = name
				break

		bar = ""
		if param:
			lst = []
			lst.append('safe')
			lst.append(param)
			lst.append('moresafe')
			lst.pop(0)
			bar = lst[0]

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

