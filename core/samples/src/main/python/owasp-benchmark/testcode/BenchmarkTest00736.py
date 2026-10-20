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

	@app.route('/benchmark/cmdi-00/BenchmarkTest00736', methods=['GET'])
	def BenchmarkTest00736_get():
		return BenchmarkTest00736_post()

	@app.route('/benchmark/cmdi-00/BenchmarkTest00736', methods=['POST'])
	def BenchmarkTest00736_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00736")
		if not param:
			param = ""

		map81232 = {}
		map81232['keyA-81232'] = 'a-Value'
		map81232['keyB-81232'] = param
		map81232['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map81232['keyB-81232']
		bar = map81232['keyA-81232']

		import platform
		import subprocess
		import helpers.utils

		argStr = ""
		if platform.system() == "Windows":
			argStr = "cmd.exe /c "
		else:
			argStr = "sh -c "
		argStr += f"echo {bar}"

		try:
			proc = subprocess.run(argStr, shell=True, capture_output=True, encoding="utf-8")

			RESPONSE += (
				helpers.utils.commandOutput(proc)
			)
		except IOError:
			RESPONSE += (
				"Problem executing cmdi - subprocess.run(list) Test Case"
			)

		return RESPONSE

