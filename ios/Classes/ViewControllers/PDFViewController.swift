//
//  PDFViewController.swift
//  uae_pass_flutter
//
//  Created by Nithin Nizam on 14/03/2024.
//

import UIKit
import PDFKit

class PDFWebViewController: UIViewController {
    var pdfURL: URL!
    var cookies: [HTTPCookie]!
    private var pdfView: PDFView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.edgesForExtendedLayout = []
                
        self.setPDFView()
        self.fetchPDF()
    }
    
    private func setPDFView() {
        DispatchQueue.main.async {
            var frame = self.view.bounds
//            frame.origin.y = 44
            frame.size.height = frame.size.height - 64
            self.pdfView = PDFView(frame: frame)
            
            self.pdfView.maxScaleFactor = 3;
            self.pdfView.minScaleFactor = 0;
//            self.pdfView.autoScales = true;
            self.pdfView.autoresizingMask = [.flexibleHeight, .flexibleWidth]
            self.view.addSubview(self.pdfView)
        }
    }
    
    private func fetchPDF() {
        DispatchQueue.global(qos: .userInitiated).async {
            let session = URLSession.shared
            let task = session.dataTask(with: URLRequest(url: self.pdfURL)) { respData, response, error in
                
                if let data = respData {
                    if let document = PDFDocument(data: data) {
                        DispatchQueue.main.async {
                            self.pdfView.document = document
                            self.addShareBarButton()
                        }
                    }
                }
            }
            session.configuration.httpCookieStorage?.storeCookies(self.cookies, for: task);
            task.resume()
        }
    }
    
    private func addShareBarButton() {
        let width = self.view.frame.width
        let height = self.view.frame.height
        let navigationBar = UINavigationBar(frame: CGRect(x: 0, y: height - 64, width: width, height: 64))
        self.view.addSubview(navigationBar);
        let navigationItem = UINavigationItem(title: "")

        let barButtonItem = UIBarButtonItem(barButtonSystemItem: .action,
                                            target: self,
                                            action: #selector(self.presentShare))
        barButtonItem.tintColor = .white
        navigationItem.rightBarButtonItem = barButtonItem
        
        let closeButtonItem = UIBarButtonItem(barButtonSystemItem: .close,
                                            target: self,
                                            action: #selector(self.close))
        closeButtonItem.tintColor = .white
        navigationItem.leftBarButtonItem = closeButtonItem
        
        navigationBar.setItems([navigationItem], animated: true)
    }
    
    @objc private func close() {
        dismiss(animated: true);
    }
    @objc private func presentShare() {
        guard let pdfDocument = self.pdfView.document?.dataRepresentation() else { return }
        
        let activityViewController = UIActivityViewController(activityItems: [pdfDocument], applicationActivities: nil)
        activityViewController.popoverPresentationController?.barButtonItem = self.navigationItem.rightBarButtonItem
        
        self.present(activityViewController, animated: true)
    }
}
